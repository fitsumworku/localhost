from __future__ import annotations

from pathlib import Path

import psycopg2
import yaml

from run_full_etl import fetch_all, load_facts


CONFIG_PATH = Path(__file__).parent / "config" / "database.yaml"


def run() -> dict[str, int]:
    config = yaml.safe_load(CONFIG_PATH.read_text())
    source = psycopg2.connect(**config["oltp"])
    target = psycopg2.connect(**config["olap"])
    try:
        accounts = fetch_all(
            source,
            "SELECT account_id, user_id, currency, created_date FROM accounts ORDER BY account_id",
        )
        dimensions = {"accounts": accounts}
        return load_facts(source, target, dimensions)
    finally:
        source.close()
        target.close()


if __name__ == "__main__":
    print(run())