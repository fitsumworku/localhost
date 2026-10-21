from unittest.mock import MagicMock
from decimal import Decimal

from src.incremental_loaders import _date_key, _decimal, load_account_event


def test_date_key_converts_date():
    from datetime import date

    assert _date_key(date(2026, 10, 19)) == 20261019
    assert _date_key("2026-10-19T12:00:00.000000Z") == 20261019


def test_decimal_accepts_numeric_base64_with_schema_scale():
    event = {
        "schema": {
            "fields": [
                {
                    "field": "after",
                    "fields": [
                        {"field": "quantity_ordered", "parameters": {"scale": "12"}}
                    ],
                }
            ]
        }
    }
    encoded = "AU342FVpmw=="
    assert _decimal(event, "quantity_ordered", encoded) == Decimal("367.206153415067")


def test_decimal_uses_oltp_scale_when_schema_is_disabled():
    assert _decimal({}, "quantity_ordered", "AU342FVpmw==") == Decimal("367.206153415067")


def test_account_loader_upserts_and_commits():
    cursor = MagicMock()
    cursor.fetchone.return_value = (17,)
    connection = MagicMock()
    connection.cursor.return_value.__enter__.return_value = cursor

    operation = load_account_event(
        connection,
        {
            "payload": {
                "op": "r",
                "after": {
                    "account_id": 3,
                    "user_id": 1,
                    "created_date": "2026-10-19T00:00:00Z",
                },
            }
        },
    )

    assert operation == "r"
    assert cursor.execute.call_count == 2
    connection.commit.assert_called_once_with()


def test_trade_value_fallback_is_derived_from_shares_and_price():
    shares = Decimal("625.941381209375")
    price = Decimal("263.320567734380")
    assert shares * price > 0