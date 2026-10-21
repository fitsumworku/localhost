from pathlib import Path

from src.cdc_consumer import CdcConsumer


def test_table_reads_debezium_source_table():
    event = {"payload": {"source": {"table": "orders"}}}
    assert CdcConsumer._table(event) == "orders"


def test_table_handles_missing_source():
    assert CdcConsumer._table({"payload": {}}) == "unknown"