from __future__ import annotations

import argparse
import os
from datetime import date, datetime
from pathlib import Path
from typing import Any, Iterable

import psycopg2
import yaml
from psycopg2.extras import RealDictCursor, execute_batch


CONFIG_PATH = Path(os.getenv("ETL_CONFIG_PATH", Path(__file__).parent / "config" / "database.yaml"))

STATUS_DIMENSIONS = {
    "dim_order_status": [
        (1, "SUBMITTED", "Submitted", False, True),
        (2, "ACCEPTED", "Accepted", False, True),
        (3, "IN_EXECUTION", "In execution", False, True),
        (4, "FILLED", "Filled", True, False),
        (5, "REJECTED", "Rejected", True, False),
        (6, "CANCELLED", "Cancelled", True, False),
    ],
    "dim_transaction_type": [
        (1, "DEPOSIT", "Deposit", True, False, "CASH"),
        (2, "WITHDRAWAL", "Withdrawal", False, True, "CASH"),
        (3, "DIVIDEND", "Dividend", True, False, "INCOME"),
        (4, "INTEREST", "Interest", True, False, "INCOME"),
        (5, "FEE", "Fee", False, True, "COST"),
    ],
    "dim_dispute_status": [
        (1, "OPEN", "Open", False, False),
        (2, "UNDER_REVIEW", "Under review", False, False),
        (3, "ESCALATED", "Escalated", False, True),
        (4, "RESOLVED", "Resolved", True, False),
        (5, "REJECTED", "Rejected", True, False),
    ],
    "dim_execution_status": [
        (1, "FILLED", "Filled", True, True),
        (2, "REJECTED", "Rejected", True, False),
        (3, "FAILED", "Failed", True, False),
    ],
}

CURRENCY_NAMES = {
    "USD": ("US Dollar", "United States", True),
    "EUR": ("Euro", "European Union", False),
    "GBP": ("Pound Sterling", "United Kingdom", False),
    "JPY": ("Japanese Yen", "Japan", False),
    "CHF": ("Swiss Franc", "Switzerland", False),
}


def fetch_all(connection: Any, query: str) -> list[dict[str, Any]]:
    with connection.cursor(cursor_factory=RealDictCursor) as cursor:
        cursor.execute(query)
        return list(cursor.fetchall())


def execute_rows(connection: Any, query: str, rows: Iterable[tuple[Any, ...]]) -> None:
    with connection.cursor() as cursor:
        execute_batch(cursor, query, list(rows), page_size=500)


def as_date(value: date | datetime | None) -> date | None:
    if value is None:
        return None
    return value.date() if isinstance(value, datetime) else value


def date_key(value: date | datetime | None) -> int | None:
    value = as_date(value)
    return value.year * 10000 + value.month * 100 + value.day if value else None


def load_static_dimensions(target: Any, currencies: set[str]) -> None:
    currency_rows = []
    for code in sorted(currencies):
        name, country, is_base = CURRENCY_NAMES.get(code, (code, None, code == "USD"))
        currency_rows.append((code, name, country, is_base))
    execute_rows(
        target,
        """
        INSERT INTO dim_currency (currency_code, currency_name, country, is_base_currency)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (currency_code) DO UPDATE SET
            currency_name = EXCLUDED.currency_name,
            country = EXCLUDED.country,
            is_base_currency = EXCLUDED.is_base_currency
        """,
        currency_rows,
    )

    execute_rows(
        target,
        """
        INSERT INTO dim_order_status
            (order_status_key, order_status_code, order_status_name, is_terminal, is_active)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (order_status_key) DO UPDATE SET
            order_status_code = EXCLUDED.order_status_code,
            order_status_name = EXCLUDED.order_status_name,
            is_terminal = EXCLUDED.is_terminal,
            is_active = EXCLUDED.is_active
        """,
        STATUS_DIMENSIONS["dim_order_status"],
    )
    execute_rows(
        target,
        """
        INSERT INTO dim_transaction_type
            (transaction_type_key, transaction_type_code, transaction_type_name,
             is_cash_inflow, is_cash_outflow, category)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (transaction_type_key) DO UPDATE SET
            transaction_type_code = EXCLUDED.transaction_type_code,
            transaction_type_name = EXCLUDED.transaction_type_name,
            is_cash_inflow = EXCLUDED.is_cash_inflow,
            is_cash_outflow = EXCLUDED.is_cash_outflow,
            category = EXCLUDED.category
        """,
        STATUS_DIMENSIONS["dim_transaction_type"],
    )
    execute_rows(
        target,
        """
        INSERT INTO dim_dispute_status
            (dispute_status_key, dispute_status_code, dispute_status_name,
             is_terminal, is_escalated)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (dispute_status_key) DO UPDATE SET
            dispute_status_code = EXCLUDED.dispute_status_code,
            dispute_status_name = EXCLUDED.dispute_status_name,
            is_terminal = EXCLUDED.is_terminal,
            is_escalated = EXCLUDED.is_escalated
        """,
        STATUS_DIMENSIONS["dim_dispute_status"],
    )
    execute_rows(
        target,
        """
        INSERT INTO dim_execution_status
            (execution_status_key, execution_status_code, execution_status_name,
             is_terminal, is_successful)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (execution_status_key) DO UPDATE SET
            execution_status_code = EXCLUDED.execution_status_code,
            execution_status_name = EXCLUDED.execution_status_name,
            is_terminal = EXCLUDED.is_terminal,
            is_successful = EXCLUDED.is_successful
        """,
        STATUS_DIMENSIONS["dim_execution_status"],
    )


def load_dates(target: Any, values: Iterable[date | datetime | None]) -> None:
    dates = sorted({as_date(value) for value in values if value is not None})
    rows = []
    for value in dates:
        rows.append(
            (
                date_key(value), value, value.day, value.strftime("%A"),
                value.isocalendar().week, value.month, value.strftime("%B"),
                (value.month - 1) // 3 + 1, value.year, value.weekday() >= 5,
            )
        )
    execute_rows(
        target,
        """
        INSERT INTO dim_date
            (date_key, full_date, day_number, day_name, week_number, month_number,
             month_name, quarter_number, year_number, is_weekend)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (date_key) DO NOTHING
        """,
        rows,
    )


def load_dimensions(source: Any, target: Any) -> dict[str, list[dict[str, Any]]]:
    users = fetch_all(source, "SELECT user_id, name, status FROM users ORDER BY user_id")
    accounts = fetch_all(
        source,
        "SELECT account_id, user_id, currency, created_date FROM accounts ORDER BY account_id",
    )
    securities = fetch_all(
        source,
        """SELECT security_id, ticker, name, asset_type, exchange, quote_currency,
                  base_currency, status, sector FROM securities ORDER BY security_id""",
    )

    currencies = {"USD"}
    currencies.update(row["currency"].strip() for row in accounts)
    for row in securities:
        currencies.add(row["quote_currency"].strip())
        if row["base_currency"]:
            currencies.add(row["base_currency"].strip())
    load_static_dimensions(target, currencies)

    execute_rows(
        target,
        """
        INSERT INTO dim_user (user_id, user_name, status, is_blacklisted)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (user_id) DO UPDATE SET
            user_name = EXCLUDED.user_name,
            status = EXCLUDED.status,
            is_blacklisted = EXCLUDED.is_blacklisted
        """,
        (
            (row["user_id"], row["name"], row["status"], "Y" if row["status"] == "SUSPENDED" else "N")
            for row in users
        ),
    )
    user_keys = {
        row["user_id"]: row["user_key"]
        for row in fetch_all(target, "SELECT user_id, user_key FROM dim_user")
    }
    currency_keys = {
        row["currency_code"].strip(): row["currency_key"]
        for row in fetch_all(target, "SELECT currency_code, currency_key FROM dim_currency")
    }
    execute_rows(
        target,
        """
        INSERT INTO dim_account (account_id, user_key, account_status, created_date)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (account_id) DO UPDATE SET
            user_key = EXCLUDED.user_key,
            account_status = EXCLUDED.account_status,
            created_date = EXCLUDED.created_date
        """,
        (
            (row["account_id"], user_keys[row["user_id"]], "ACTIVE", as_date(row["created_date"]))
            for row in accounts
        ),
    )
    execute_rows(
        target,
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
            (
                row["security_id"], row["ticker"], row["name"], row["sector"],
                row["asset_type"], row["exchange"], row["status"],
                currency_keys[row["quote_currency"].strip()],
                currency_keys.get(row["base_currency"].strip()) if row["base_currency"] else None,
            )
            for row in securities
        ),
    )
    target.commit()
    return {"users": users, "accounts": accounts, "securities": securities}


def load_facts(source: Any, target: Any, dimensions: dict[str, list[dict[str, Any]]]) -> dict[str, int]:
    positions = fetch_all(source, "SELECT * FROM account_positions ORDER BY position_id")
    orders = fetch_all(source, "SELECT * FROM orders ORDER BY order_id")
    executions = fetch_all(source, "SELECT * FROM executions ORDER BY execution_id")
    trades = fetch_all(source, "SELECT * FROM trades ORDER BY trade_id")
    transactions = fetch_all(source, "SELECT * FROM transactions ORDER BY transaction_id")
    disputes = fetch_all(source, "SELECT * FROM disputes ORDER BY dispute_id")

    account_keys = {row["account_id"]: row["account_key"] for row in fetch_all(target, "SELECT account_id, account_key FROM dim_account")}
    user_keys = {row["user_id"]: row["user_key"] for row in fetch_all(target, "SELECT user_id, user_key FROM dim_user")}
    security_keys = {row["security_id"]: row["security_key"] for row in fetch_all(target, "SELECT security_id, security_key FROM dim_security")}
    currency_keys = {row["currency_code"].strip(): row["currency_key"] for row in fetch_all(target, "SELECT currency_code, currency_key FROM dim_currency")}
    order_status_keys = {row["order_status_code"]: row["order_status_key"] for row in fetch_all(target, "SELECT order_status_code, order_status_key FROM dim_order_status")}
    transaction_type_keys = {row["transaction_type_code"]: row["transaction_type_key"] for row in fetch_all(target, "SELECT transaction_type_code, transaction_type_key FROM dim_transaction_type")}
    dispute_status_keys = {row["dispute_status_code"]: row["dispute_status_key"] for row in fetch_all(target, "SELECT dispute_status_code, dispute_status_key FROM dim_dispute_status")}
    execution_status_keys = {row["execution_status_code"]: row["execution_status_key"] for row in fetch_all(target, "SELECT execution_status_code, execution_status_key FROM dim_execution_status")}
    account_users = {row["account_id"]: row["user_id"] for row in dimensions["accounts"]}

    all_dates = []
    all_dates += [row["created_date"] for row in dimensions["accounts"]]
    all_dates += [row["created_date"] for row in orders] + [row["accepted_at"] for row in orders] + [row["terminal_at"] for row in orders]
    all_dates += [row["started_at"] for row in executions] + [row["finished_at"] for row in executions]
    all_dates += [row["trade_date"] for row in trades] + [row["settlement_date"] for row in trades]
    all_dates += [row["transaction_date"] for row in transactions] + [row["completed_at"] for row in transactions]
    all_dates += [row["date_created"] for row in disputes] + [row["date_resolved"] for row in disputes]
    load_dates(target, all_dates)

    with target.cursor() as cursor:
        for table in ("fact_disputes", "fact_trades", "fact_executions", "fact_orders", "fact_transactions"):
            cursor.execute(f"DELETE FROM {table}")
    target.commit()

    execute_rows(
        target,
        """INSERT INTO fact_orders
        (order_id, submission_date_key, user_key, account_key, security_key, order_status_key,
         side, quantity_ordered, requested_amount, accepted_at_date_key, terminal_at_date_key, rejection_reason)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (
            (row["order_id"], date_key(row["created_date"]), user_keys[account_users[row["account_id"]]], account_keys[row["account_id"]], security_keys[row["security_id"]], order_status_keys[row["order_status"]], row["side"], row["quantity_ordered"], row["requested_amount"], date_key(row["accepted_at"]), date_key(row["terminal_at"]), row["rejection_reason"])
            for row in orders
        ),
    )
    execute_rows(
        target,
        """INSERT INTO fact_executions
        (execution_id, order_id, started_at_date_key, finished_at_date_key, user_key, account_key,
         security_key, execution_status_key, side, quantity_filled, price_of_execution, cash_amount,
         quote_price, quote_currency_key, fx_rate_to_usd, failure_reason, exchange_trade_id)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (
            (row["execution_id"], row["order_id"], date_key(row["started_at"]), date_key(row["finished_at"]), user_keys[account_users[row["account_id"]]], account_keys[row["account_id"]], security_keys[row["security_id"]], execution_status_keys[row["status_of_execution"]], row["side"], row["quantity_filled"], row["price_of_execution"], row["cash_amount"], row["quote_price"], currency_keys.get(row["quote_currency"].strip()) if row["quote_currency"] else None, row["fx_rate_to_usd"], row["failure_reason"], row["exchange_trade_id"])
            for row in executions
        ),
    )
    execute_rows(
        target,
        """INSERT INTO fact_trades
        (trade_id, execution_id, trade_date_key, settlement_date_key, user_key, account_key,
         security_key, trade_type, shares, trade_price, trade_value, status_of_trade)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (
            (row["trade_id"], row["execution_id"], date_key(row["trade_date"]), date_key(row["settlement_date"]), user_keys[account_users[row["account_id"]]], account_keys[row["account_id"]], security_keys[next(execution["security_id"] for execution in executions if execution["execution_id"] == row["execution_id"])], next(execution["side"] for execution in executions if execution["execution_id"] == row["execution_id"]), next(execution["quantity_filled"] for execution in executions if execution["execution_id"] == row["execution_id"]), next(execution["price_of_execution"] for execution in executions if execution["execution_id"] == row["execution_id"]), next(execution["cash_amount"] for execution in executions if execution["execution_id"] == row["execution_id"]), "COMPLETED")
            for row in trades
        ),
    )
    execute_rows(
        target,
        """INSERT INTO fact_transactions
        (transaction_id, transaction_date_key, completed_at_date_key, user_key, account_key,
         transaction_type_key, transaction_amount, transaction_status, failure_reason)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (
            (row["transaction_id"], date_key(row["transaction_date"]), date_key(row["completed_at"]), user_keys[account_users[row["account_id"]]], account_keys[row["account_id"]], transaction_type_keys[row["transaction_type"]], row["transaction_amount"], row["transaction_status"], row["failure_reason"])
            for row in transactions
        ),
    )
    execute_rows(
        target,
        """INSERT INTO fact_disputes
        (dispute_id, created_date_key, resolved_date_key, user_key, admin_key, account_key,
         dispute_status_key, is_transaction_dispute, dispute_type, description)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (
            (row["dispute_id"], date_key(row["date_created"]), date_key(row["date_resolved"]), user_keys[account_users[row["account_id"]]], user_keys.get(row["admin_id"]) if row["admin_id"] else None, account_keys[row["account_id"]], dispute_status_keys[row["status"]], row["transaction_id"] is not None, row["dispute_type"], row["description"])
            for row in disputes
        ),
    )
    target.commit()
    return {
        "orders": len(orders), "executions": len(executions), "trades": len(trades),
        "transactions": len(transactions), "disputes": len(disputes),
    }


def run() -> dict[str, int]:
    config = yaml.safe_load(CONFIG_PATH.read_text())
    source = psycopg2.connect(**config["oltp"])
    target = psycopg2.connect(**config["olap"])
    try:
        dimensions = load_dimensions(source, target)
        counts = load_facts(source, target, dimensions)
        counts.update({"users": len(dimensions["users"]), "accounts": len(dimensions["accounts"]), "securities": len(dimensions["securities"])})
        return counts
    finally:
        source.close()
        target.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Full OLTP to OLAP snapshot ETL")
    parser.parse_args()
    print(run())