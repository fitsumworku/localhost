from __future__ import annotations

import argparse
import json
import time
from collections import Counter
from pathlib import Path
from typing import Any

import yaml
from kafka import KafkaConsumer


CONFIG_PATH = Path(__file__).parent / "config" / "database.yaml"


def event_operation(event: dict[str, Any]) -> str:
    return (event.get("payload") or event).get("op", "unknown")


def event_key(event: dict[str, Any]) -> Any:
    payload = event.get("payload") or event
    row = payload.get("after") or payload.get("before") or {}
    for key, value in row.items():
        if key.lower().endswith("_id"):
            return value
    return None


def consume_topic(
    bootstrap_servers: str,
    topic: str,
    group_id: str,
    max_messages: int,
    idle_limit: int,
) -> dict[str, Any]:
    consumer = KafkaConsumer(
        topic,
        bootstrap_servers=bootstrap_servers,
        group_id=group_id,
        auto_offset_reset="earliest",
        enable_auto_commit=True,
        request_timeout_ms=15000,
        consumer_timeout_ms=1000,
        value_deserializer=lambda value: json.loads(value.decode("utf-8")),
    )
    events: list[dict[str, Any]] = []
    idle_polls = 0
    try:
        while len(events) < max_messages and idle_polls < idle_limit:
            records = consumer.poll(timeout_ms=1000, max_records=max_messages - len(events))
            messages = [message for batch in records.values() for message in batch]
            if not messages:
                idle_polls += 1
                continue
            idle_polls = 0
            events.extend(message.value for message in messages)
    finally:
        consumer.close()

    operations = Counter(event_operation(event) for event in events)
    keys = [event_key(event) for event in events]
    return {"count": len(events), "operations": dict(operations), "keys": keys}


def main() -> None:
    parser = argparse.ArgumentParser(description="Run bounded read-only smoke tests for Debezium topics")
    parser.add_argument("--max-messages", type=int, default=6)
    parser.add_argument("--idle-seconds", type=int, default=10)
    parser.add_argument("--group-prefix", default="etl-topic-smoke")
    args = parser.parse_args()

    config = yaml.safe_load(CONFIG_PATH.read_text())
    topics = [topic for topic in config["kafka"]["topics"] if not topic.endswith(".public.users")]
    idle_limit = max(1, args.idle_seconds)
    run_id = int(time.time())

    for topic in topics:
        group_id = f"{args.group_prefix}-{run_id}-{topic.rsplit('.', 1)[-1]}"
        result = consume_topic(
            config["kafka"]["bootstrap_servers"],
            topic,
            group_id,
            args.max_messages,
            idle_limit,
        )
        print(f"{topic}: {result['count']} events; operations={result['operations']}; keys={result['keys']}")


if __name__ == "__main__":
    main()