from __future__ import annotations

import base64
from datetime import date, datetime
from decimal import Decimal
from typing import Any


DECIMAL_SCALES = {
    "quantity_ordered": 12,
    "requested_amount": 2,
    "quantity_filled": 12,
    "price_of_execution": 12,
    "cash_amount": 2,
    "quote_price": 12,
    "fx_rate_to_usd": 12,
    "transaction_amount": 2,
}


def _payload(event: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    payload = event.get("payload", event)
    return payload.get("op", ""), payload.get("after") or payload.get("before") or {}


def _decimal(event: dict[str, Any], field: str, value: Any) -> Any:
    if value is None or isinstance(value, (int, float, Decimal)):
        return value
    if not isinstance(value, str):
        return value
    try:
        return Decimal(value)
    except Exception:
        pass

    schema = event.get("schema", {})
    if not schema and isinstance(event.get("payload"), dict):
        schema = event["payload"].get("schema", {})

    def find_field(node: Any) -> dict[str, Any] | None:
        if isinstance(node, dict):
            if node.get("field") == field:
                return node
            for child in node.values():
                found = find_field(child)
                if found is not None:
                    return found
        elif isinstance(node, list):
            for child in node:
                found = find_field(child)
                if found is not None:
                    return found
        return None

    field_schema = find_field(schema)
    if field_schema is not None:
        scale = int(field_schema.get("parameters", {}).get("scale", 0))
    elif field in DECIMAL_SCALES:
        scale = DECIMAL_SCALES[field]
    else:
        raise ValueError(f"Unable to determine numeric scale for field={field}")

    if field_schema is not None or field in DECIMAL_SCALES:
        raw = base64.b64decode(value)
        unscaled = int.from_bytes(raw, byteorder="big", signed=True)
        return Decimal(unscaled) / (Decimal(10) ** scale)
    raise ValueError(f"Unable to decode numeric field={field}: {value}")


def _date_key(value: date | datetime | None) -> int | None:
    if value is None:
        return None
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    value = value.date() if isinstance(value, datetime) else value
    return value.year * 10000 + value.month * 100 + value.day


def _as_date(value: date | datetime | str) -> date:
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return value.date() if isinstance(value, datetime) else value


def load_account_event(connection: Any, event: dict[str, Any]) -> str:
    operation, row = _payload(event)
    if operation == "d":
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM dim_account WHERE account_id = %s", (row["account_id"],))
        connection.commit()
        return operation
    if operation not in {"c", "r", "u"}:
        return operation

    with connection.cursor() as cursor:
        cursor.execute("SELECT user_key FROM dim_user WHERE user_id = %s", (row["user_id"],))
        result = cursor.fetchone()
        if result is None:
            raise ValueError(f"Missing DIM_USER for account user_id={row['user_id']}")
        cursor.execute(
            """
            INSERT INTO dim_account (account_id, user_key, account_status, created_date)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (account_id) DO UPDATE SET
                user_key = EXCLUDED.user_key,
                account_status = EXCLUDED.account_status,
                created_date = EXCLUDED.created_date
            """,
            (row["account_id"], result[0], "ACTIVE", _as_date(row["created_date"])),
        )
    connection.commit()
    return operation


def load_security_event(connection: Any, event: dict[str, Any]) -> str:
    operation, row = _payload(event)
    if operation == "d":
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM dim_security WHERE security_id = %s", (row["security_id"],))
        connection.commit()
        return operation
    if operation not in {"c", "r", "u"}:
        return operation

    with connection.cursor() as cursor:
        cursor.execute(
            "SELECT currency_key FROM dim_currency WHERE currency_code = %s",
            (row["quote_currency"].strip(),),
        )
        quote_currency = cursor.fetchone()
        if quote_currency is None:
            raise ValueError(f"Missing quote currency={row['quote_currency']}")
        base_currency = None
        if row.get("base_currency"):
            cursor.execute(
                "SELECT currency_key FROM dim_currency WHERE currency_code = %s",
                (row["base_currency"].strip(),),
            )
            base_currency = cursor.fetchone()
            if base_currency is None:
                raise ValueError(f"Missing base currency={row['base_currency']}")
        cursor.execute(
            """
            INSERT INTO dim_security
                (security_id, ticker, company_name, sector, asset_type, exchange,
                 security_status, quote_currency_key, base_currency_key)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (security_id) DO UPDATE SET
                ticker = EXCLUDED.ticker,
                company_name = EXCLUDED.company_name,
                sector = EXCLUDED.sector,
                asset_type = EXCLUDED.asset_type,
                exchange = EXCLUDED.exchange,
                security_status = EXCLUDED.security_status,
                quote_currency_key = EXCLUDED.quote_currency_key,
                base_currency_key = EXCLUDED.base_currency_key
            """,
            (
                row["security_id"], row["ticker"], row["name"], row.get("sector"),
                row["asset_type"], row["exchange"], row["status"], quote_currency[0],
                base_currency[0] if base_currency else None,
            ),
        )
    connection.commit()
    return operation


def load_order_event(connection: Any, event: dict[str, Any]) -> str:
    operation, row = _payload(event)
    if operation == "d":
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM fact_orders WHERE order_id = %s", (row["order_id"],))
        connection.commit()
        return operation
    if operation not in {"c", "r", "u"}:
        return operation
    quantity_ordered = _decimal(event, "quantity_ordered", row.get("quantity_ordered"))
    requested_amount = _decimal(event, "requested_amount", row.get("requested_amount"))

    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT a.account_key, a.user_key, s.security_key, os.order_status_key
            FROM dim_account a
            JOIN dim_security s ON s.security_id = %s
            JOIN dim_order_status os ON os.order_status_code = %s
            WHERE a.account_id = %s
            """,
            (row["security_id"], row["order_status"], row["account_id"]),
        )
        keys = cursor.fetchone()
        if keys is None:
            raise ValueError(
                f"Missing account, security, or order status for order_id={row['order_id']}"
            )
        cursor.execute("DELETE FROM fact_orders WHERE order_id = %s", (row["order_id"],))
        cursor.execute(
            """
            INSERT INTO fact_orders
                (order_id, submission_date_key, user_key, account_key, security_key,
                 order_status_key, side, quantity_ordered, requested_amount,
                 accepted_at_date_key, terminal_at_date_key, rejection_reason)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                row["order_id"], _date_key(row["created_date"]), keys[1], keys[0], keys[2],
                keys[3], row["side"], quantity_ordered, requested_amount,
                _date_key(row.get("accepted_at")), _date_key(row.get("terminal_at")),
                row.get("rejection_reason"),
            ),
        )
    connection.commit()
    return operation


def _account_keys(cursor: Any, account_id: int) -> tuple[int, int]:
    cursor.execute(
        "SELECT account_key, user_key FROM dim_account WHERE account_id = %s",
        (account_id,),
    )
    result = cursor.fetchone()
    if result is None:
        raise ValueError(f"Missing DIM_ACCOUNT for account_id={account_id}")
    return result[0], result[1]


def load_execution_event(connection: Any, event: dict[str, Any]) -> str:
    operation, row = _payload(event)
    if operation == "d":
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM fact_executions WHERE execution_id = %s", (row["execution_id"],))
        connection.commit()
        return operation
    if operation not in {"c", "r", "u"}:
        return operation
    quantity_filled = _decimal(event, "quantity_filled", row.get("quantity_filled"))
    price_of_execution = _decimal(event, "price_of_execution", row.get("price_of_execution"))
    cash_amount = _decimal(event, "cash_amount", row.get("cash_amount"))
    quote_price = _decimal(event, "quote_price", row.get("quote_price"))
    fx_rate = _decimal(event, "fx_rate_to_usd", row.get("fx_rate_to_usd"))

    with connection.cursor() as cursor:
        account_key, user_key = _account_keys(cursor, row["account_id"])
        cursor.execute("SELECT security_key FROM dim_security WHERE security_id = %s", (row["security_id"],))
        security = cursor.fetchone()
        cursor.execute("SELECT execution_status_key FROM dim_execution_status WHERE execution_status_code = %s", (row["status_of_execution"],))
        status = cursor.fetchone()
        if security is None or status is None:
            raise ValueError(f"Missing execution dependency for execution_id={row['execution_id']}")
        currency_key = None
        if row.get("quote_currency"):
            cursor.execute("SELECT currency_key FROM dim_currency WHERE currency_code = %s", (row["quote_currency"].strip(),))
            currency = cursor.fetchone()
            if currency is None:
                raise ValueError(f"Missing quote currency={row['quote_currency']}")
            currency_key = currency[0]
        cursor.execute("DELETE FROM fact_executions WHERE execution_id = %s", (row["execution_id"],))
        cursor.execute(
            """
            INSERT INTO fact_executions
                (execution_id, order_id, started_at_date_key, finished_at_date_key, user_key,
                 account_key, security_key, execution_status_key, side, quantity_filled,
                 price_of_execution, cash_amount, quote_price, quote_currency_key,
                 fx_rate_to_usd, failure_reason, exchange_trade_id)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                row["execution_id"], row["order_id"], _date_key(row["started_at"]),
                _date_key(row["finished_at"]), user_key, account_key, security[0], status[0],
                row["side"], quantity_filled, price_of_execution, cash_amount,
                quote_price, currency_key, fx_rate, row.get("failure_reason"),
                row.get("exchange_trade_id"),
            ),
        )
    connection.commit()
    return operation


def load_trade_event(connection: Any, event: dict[str, Any]) -> str:
    operation, row = _payload(event)
    if operation == "d":
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM fact_trades WHERE trade_id = %s", (row["trade_id"],))
        connection.commit()
        return operation
    if operation not in {"c", "r", "u"}:
        return operation

    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT execution_id, user_key, account_key, security_key, side,
                   quantity_filled, price_of_execution, cash_amount
            FROM fact_executions
            WHERE execution_id = %s AND account_key = (
                SELECT account_key FROM dim_account WHERE account_id = %s
            )
            """,
            (row["execution_id"], row["account_id"]),
        )
        execution = cursor.fetchone()
        if execution is None:
            raise ValueError(f"Missing FACT_EXECUTIONS for trade_id={row['trade_id']}")
        trade_value = execution[7]
        if trade_value is None and execution[5] is not None and execution[6] is not None:
            trade_value = execution[5] * execution[6]
        cursor.execute("DELETE FROM fact_trades WHERE trade_id = %s", (row["trade_id"],))
        cursor.execute(
            """
            INSERT INTO fact_trades
                (trade_id, execution_id, trade_date_key, settlement_date_key, user_key,
                 account_key, security_key, trade_type, shares, trade_price, trade_value,
                 status_of_trade)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                row["trade_id"], execution[0], _date_key(row["trade_date"]),
                _date_key(row["settlement_date"]), execution[1], execution[2], execution[3],
                execution[4], execution[5], execution[6], trade_value, "COMPLETED",
            ),
        )
    connection.commit()
    return operation


def load_transaction_event(connection: Any, event: dict[str, Any]) -> str:
    operation, row = _payload(event)
    if operation == "d":
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM fact_transactions WHERE transaction_id = %s", (row["transaction_id"],))
        connection.commit()
        return operation
    if operation not in {"c", "r", "u"}:
        return operation
    transaction_amount = _decimal(event, "transaction_amount", row.get("transaction_amount"))

    with connection.cursor() as cursor:
        account_key, user_key = _account_keys(cursor, row["account_id"])
        cursor.execute("SELECT transaction_type_key FROM dim_transaction_type WHERE transaction_type_code = %s", (row["transaction_type"],))
        transaction_type = cursor.fetchone()
        if transaction_type is None:
            raise ValueError(f"Missing transaction type={row['transaction_type']}")
        cursor.execute("DELETE FROM fact_transactions WHERE transaction_id = %s", (row["transaction_id"],))
        cursor.execute(
            """
            INSERT INTO fact_transactions
                (transaction_id, transaction_date_key, completed_at_date_key, user_key,
                 account_key, transaction_type_key, transaction_amount, transaction_status,
                 failure_reason)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                row["transaction_id"], _date_key(row["transaction_date"]),
                _date_key(row.get("completed_at")), user_key, account_key, transaction_type[0],
                transaction_amount, row["transaction_status"], row.get("failure_reason"),
            ),
        )
    connection.commit()
    return operation


def load_dispute_event(connection: Any, event: dict[str, Any]) -> str:
    operation, row = _payload(event)
    if operation == "d":
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM fact_disputes WHERE dispute_id = %s", (row["dispute_id"],))
        connection.commit()
        return operation
    if operation not in {"c", "r", "u"}:
        return operation

    with connection.cursor() as cursor:
        account_key, user_key = _account_keys(cursor, row["account_id"])
        cursor.execute("SELECT dispute_status_key FROM dim_dispute_status WHERE dispute_status_code = %s", (row["status"],))
        status = cursor.fetchone()
        if status is None:
            raise ValueError(f"Missing dispute status={row['status']}")
        admin_key = None
        if row.get("admin_id"):
            cursor.execute("SELECT user_key FROM dim_user WHERE user_id = %s", (row["admin_id"],))
            admin = cursor.fetchone()
            if admin is None:
                raise ValueError(f"Missing admin user_id={row['admin_id']}")
            admin_key = admin[0]
        cursor.execute("DELETE FROM fact_disputes WHERE dispute_id = %s", (row["dispute_id"],))
        cursor.execute(
            """
            INSERT INTO fact_disputes
                (dispute_id, created_date_key, resolved_date_key, user_key, admin_key,
                 account_key, dispute_status_key, is_transaction_dispute, dispute_type,
                 description)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                row["dispute_id"], _date_key(row["date_created"]),
                _date_key(row.get("date_resolved")), user_key, admin_key, account_key,
                status[0], row.get("transaction_id") is not None, row["dispute_type"],
                row.get("description"),
            ),
        )
    connection.commit()
    return operation