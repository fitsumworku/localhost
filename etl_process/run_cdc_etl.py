from __future__ import annotations

import argparse
import logging
from pathlib import Path

import yaml

from run_full_etl import run as run_full_etl
from src.cdc_consumer import CdcConsumer
from src.monitoring import configure_logging

CONFIG_PATH = Path(__file__).parent / "config" / "database.yaml"


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the multi-topic Debezium CDC consumer")
    parser.add_argument("--max-messages", type=int)
    parser.add_argument("--dead-letter", type=Path, default=Path("dead_letter.jsonl"))
    parser.add_argument("--group-id")
    parser.add_argument("--offset-reset", choices=("earliest", "latest"), default="earliest")
    parser.add_argument("--idle-seconds", type=int, default=60)
    args = parser.parse_args()
    configure_logging()
    processed = CdcConsumer(
        CONFIG_PATH,
        run_full_etl,
        args.dead_letter,
        group_id=args.group_id,
        auto_offset_reset=args.offset_reset,
        idle_limit=args.idle_seconds,
    ).run(args.max_messages)
    logging.getLogger("etl.cdc").info("Processed %s CDC messages", processed)


if __name__ == "__main__":
    main()