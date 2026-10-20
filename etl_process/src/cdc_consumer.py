from __future__ import annotations

import json
import logging
import time
from pathlib import Path
from typing import Any, Callable

import psycopg2
import yaml
from kafka import KafkaConsumer

from src.loaders import apply_user_change
from src.transformers import transform_user_event
from src.validators import validate_user_change
from src.incremental_loaders import (
    load_account_event,
    load_dispute_event,
    load_execution_event,
    load_order_event,
    load_security_event,
    load_trade_event,
    load_transaction_event,
)

LOGGER = logging.getLogger("etl.cdc")
RETRY_LIMIT = 3


class CdcConsumer:
    def __init__(
        self,
        config_path: Path,
        full_refresh: Callable[[], dict[str, int]],
        dead_letter_path: Path = Path("dead_letter.jsonl"),
        group_id: str | None = None,
        auto_offset_reset: str = "earliest",
        idle_limit: int = 60,
    ) -> None:
        config = yaml.safe_load(config_path.read_text())
        kafka_config = config["kafka"]
        self.consumer = KafkaConsumer(
            *kafka_config["topics"],
            bootstrap_servers=kafka_config["bootstrap_servers"],
            group_id=group_id or kafka_config["group_id"],
            auto_offset_reset=auto_offset_reset,
            enable_auto_commit=False,
            max_poll_interval_ms=7200000,
            max_poll_records=1,
            value_deserializer=lambda value: json.loads(value.decode("utf-8")),
        )
        self.olap = psycopg2.connect(**config["olap"])
        self.full_refresh = full_refresh
        self.dead_letter_path = dead_letter_path
        self.idle_limit = idle_limit

    @staticmethod
    def _table(event: dict[str, Any]) -> str:
        payload = event.get("payload", event)
        return payload.get("source", {}).get("table", "unknown")

    def _dead_letter(self, message: Any, error: Exception) -> None:
        record = {
            "topic": message.topic,
            "partition": message.partition,
            "offset": message.offset,
            "error": str(error),
            "value": message.value,
        }
        with self.dead_letter_path.open("a", encoding="utf-8") as output:
            output.write(json.dumps(record, default=str) + "\n")

    def _apply_users(self, event: dict[str, Any]) -> None:
        change = transform_user_event(event)
        if change is None:
            return
        validate_user_change(change)
        apply_user_change(self.olap, change)

    def _apply_event(self, event: dict[str, Any]) -> None:
        table = self._table(event)
        handlers = {
            "users": self._apply_users,
            "accounts": lambda value: load_account_event(self.olap, value),
            "securities": lambda value: load_security_event(self.olap, value),
            "orders": lambda value: load_order_event(self.olap, value),
            "executions": lambda value: load_execution_event(self.olap, value),
            "trades": lambda value: load_trade_event(self.olap, value),
            "transactions": lambda value: load_transaction_event(self.olap, value),
            "disputes": lambda value: load_dispute_event(self.olap, value),
        }
        handler = handlers.get(table)
        if handler is None:
            LOGGER.info("Skipping source table without an OLAP target: %s", table)
            return
        handler(event)

    def _process_batch(self, messages: list[Any]) -> None:
        for message in messages:
            self._apply_event(message.value)

    def run(self, max_messages: int | None = None, idle_limit: int | None = None) -> int:
        processed = 0
        idle_polls = 0
        idle_limit = idle_limit or self.idle_limit
        try:
            while max_messages is None or processed < max_messages:
                records = self.consumer.poll(timeout_ms=1000, max_records=1)
                messages = [message for batch in records.values() for message in batch]
                if not messages:
                    idle_polls += 1
                    if max_messages is not None and idle_polls >= idle_limit:
                        break
                    continue
                idle_polls = 0
                for attempt in range(RETRY_LIMIT):
                    try:
                        self._process_batch(messages)
                        self.consumer.commit()
                        processed += len(messages)
                        break
                    except Exception as error:
                        self.olap.rollback()
                        if attempt == RETRY_LIMIT - 1:
                            for message in messages:
                                self._dead_letter(message, error)
                            raise
                        LOGGER.exception("CDC batch failed; retrying")
                        time.sleep(2 ** attempt)
        finally:
            self.consumer.close()
            self.olap.close()
        return processed