from __future__ import annotations

import argparse
import json
import os
import time
from pathlib import Path
from typing import Any, Callable

import psycopg2
import yaml
from kafka import KafkaConsumer

from src.incremental_loaders import (
    load_account_event,
    load_dispute_event,
    load_execution_event,
    load_order_event,
    load_security_event,
    load_trade_event,
    load_transaction_event,
)
from src.loaders import apply_user_change
from src.transformers import transform_user_event
from src.validators import validate_user_change


CONFIG_PATH = Path(os.getenv("ETL_CONFIG_PATH", Path(__file__).parent / "config" / "database.yaml"))
PIPELINE = [
    ("accounts", load_account_event),
    ("securities", load_security_event),
    ("orders", load_order_event),
    ("executions", load_execution_event),
    ("trades", load_trade_event),
    ("transactions", load_transaction_event),
    ("disputes", load_dispute_event),
]


def read_one(
    bootstrap_servers: str,
    topic: str,
    group_id: str,
    timeout_seconds: int,
) -> dict[str, Any] | None:
    consumer = KafkaConsumer(
        topic,
        bootstrap_servers=bootstrap_servers,
        group_id=group_id,
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        request_timeout_ms=15000,
        consumer_timeout_ms=1000,
        value_deserializer=lambda value: json.loads(value.decode("utf-8")),
    )
    deadline = time.monotonic() + timeout_seconds
    try:
        while time.monotonic() < deadline:
            records = consumer.poll(timeout_ms=1000, max_records=1)
            messages = [message for batch in records.values() for message in batch]
            if messages:
                return messages[0].value
        return None
    finally:
        consumer.close()


def apply_user(connection: Any, event: dict[str, Any]) -> None:
    change = transform_user_event(event)
    if change is not None:
        validate_user_change(change)
        apply_user_change(connection, change)


def run(timeout_seconds: int = 15) -> None:
    config = yaml.safe_load(CONFIG_PATH.read_text())
    topics = config["kafka"]["topics"]
    bootstrap_servers = config["kafka"]["bootstrap_servers"]
    connection = psycopg2.connect(**config["olap"])
    run_id = int(time.time())

    try:
        topic_handlers: list[tuple[str, Callable[[Any, dict[str, Any]], Any]]] = [
            ("users", apply_user),
            *PIPELINE,
        ]
        for table, handler in topic_handlers:
            topic = next(topic for topic in topics if topic.endswith(f".public.{table}"))
            event = read_one(bootstrap_servers, topic, f"etl-smoke-{table}-{run_id}", timeout_seconds)
            if event is None:
                raise RuntimeError(f"No event received for {table}")
            handler(connection, event)
            print(f"{table}: applied op={event.get('payload', event).get('op')}")
    finally:
        connection.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Apply one event per supported CDC pipeline")
    parser.add_argument("--timeout-seconds", type=int, default=15)
    args = parser.parse_args()
    run(args.timeout_seconds)


if __name__ == "__main__":
    main()