from __future__ import annotations

import argparse
from pathlib import Path

import psycopg2
import yaml

from src.kafka_consumer import user_events
from src.loaders import apply_user_change
from src.validators import validate_user_change
from src.transformers import transform_user_event


CONFIG_PATH = Path(__file__).parent / "config" / "database.yaml"


def run(max_messages: int | None = None) -> int:
    config = yaml.safe_load(CONFIG_PATH.read_text())
    kafka_config = config["kafka"]
    connection = psycopg2.connect(**config["olap"])
    processed = 0

    try:
        events = user_events(
            bootstrap_servers=kafka_config["bootstrap_servers"],
            topic=kafka_config["users_topic"],
            group_id=kafka_config["group_id"],
        )
        for event in events:
            change = transform_user_event(event)
            if change is None:
                continue
            validate_user_change(change)
            apply_user_change(connection, change)
            processed += 1
            if max_messages is not None and processed >= max_messages:
                break
    finally:
        connection.close()

    return processed


def main() -> None:
    parser = argparse.ArgumentParser(description="Load Debezium users events into OLAP")
    parser.add_argument("--max-messages", type=int)
    args = parser.parse_args()
    print(f"Processed {run(args.max_messages)} user events")


if __name__ == "__main__":
    main()