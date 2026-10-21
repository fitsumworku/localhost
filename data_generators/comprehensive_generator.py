#!/usr/bin/env python3
"""Generate executions, trades, transactions, and cash-ledger rows."""

import random
import re
import uuid
from datetime import datetime, timedelta
from decimal import Decimal
from pathlib import Path

data_folder = Path(__file__).parent.parent / "data"


def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, datetime):
        return "'" + str(value) + "'"
    if isinstance(value, (float, Decimal)):
        return f"{value:.12f}"
    if isinstance(value, str):
        return "'" + value.replace("'", "''") + "'"
    return str(value)


securities_by_id = {}
security_pattern = (
    r"VALUES\s*\((\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',"
    r"\s*'([^']*)',\s*'([^']*)',\s*([^,]*),\s*'([^']*)'"
)
for match in re.findall(security_pattern, (data_folder / "securities_insert.sql").read_text(encoding="utf-8")):
    security_id, _, _, asset_type, _, quote_currency, _, status = match
    securities_by_id[int(security_id)] = {
        "asset_type": asset_type,
        "quote_currency": quote_currency,
        "status": status,
    }

accounts_by_id = {}
account_pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*'[^']+',"
for account_id, user_id in re.findall(account_pattern, (data_folder / "accounts_insert.sql").read_text(encoding="utf-8")):
    accounts_by_id[int(account_id)] = {"user_id": int(user_id)}

account_cash_balances = {}
balance_pattern = r"VALUES\s*\((\d+),\s*([\d.]+),"
for account_id, balance in re.findall(balance_pattern, (data_folder / "account_cash_balances_insert.sql").read_text(encoding="utf-8")):
    account_cash_balances[int(account_id)] = float(balance)

orders_by_id = {}
order_pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*'[^']+',\s*'([BS])'"
for order_id, account_id, security_id, side in re.findall(order_pattern, (data_folder / "orders_insert.sql").read_text(encoding="utf-8")):
    orders_by_id[int(order_id)] = {
        "account_id": int(account_id),
        "security_id": int(security_id),
        "side": side,
    }

print(f"Parsed {len(securities_by_id)} securities, {len(accounts_by_id)} accounts, {len(orders_by_id)} orders")


def generate_fx_rate(quote_currency):
    if quote_currency == "USD":
        return 1.0
    return {
        "EUR": random.uniform(1.05, 1.15),
        "GBP": random.uniform(1.20, 1.35),
        "JPY": random.uniform(0.006, 0.008),
        "CHF": random.uniform(1.05, 1.15),
    }.get(quote_currency, random.uniform(0.80, 1.20))


executions = []
for execution_id, (order_id, order) in enumerate(orders_by_id.items(), start=1):
    security = securities_by_id.get(order["security_id"])
    if not security:
        continue

    status = random.choices(["FILLED", "REJECTED", "FAILED"], weights=[70, 20, 10])[0]
    finished_at = datetime.now() - timedelta(days=random.randint(0, 90))
    started_at = finished_at - timedelta(seconds=random.randint(1, 30))
    is_filled = status == "FILLED"
    needs_fx_conversion = is_filled and security["quote_currency"] != "USD"
    quote_currency = security["quote_currency"] if is_filled else None
    quote_price = (
        Decimal(str(random.uniform(10, 500))).quantize(Decimal("0.000000000001"))
        if is_filled else None
    )
    fx_rate = (
        Decimal(str(generate_fx_rate(quote_currency))).quantize(Decimal("0.000000000001"))
        if needs_fx_conversion
        else (Decimal("1.000000000000") if is_filled else None)
    )
    quote_timestamp = finished_at - timedelta(seconds=random.randint(1, 20)) if is_filled else None

    executions.append({
        "execution_id": execution_id,
        "order_id": order_id,
        "account_id": order["account_id"],
        "security_id": order["security_id"],
        "side": order["side"],
        "status": status,
        "started_at": started_at,
        "finished_at": finished_at,
        "quote_price": quote_price,
        "quote_currency": quote_currency,
        "quote_timestamp": quote_timestamp,
        "quote_source": "market-data-api" if is_filled else None,
        "fx_rate": fx_rate,
        "fx_quote_timestamp": quote_timestamp if needs_fx_conversion else None,
        "fx_source": "fx-rates-api" if needs_fx_conversion else None,
        "quantity_filled": round(random.uniform(10, 1000), 12) if is_filled else None,
        "price_of_execution": None,
        "exchange_trade_id": f"EXC-{execution_id:08d}" if is_filled else None,
        "failure_reason": None if is_filled else random.choice(["Insufficient funds", "Market closed", "System error"]),
    })
    if is_filled:
        executions[-1]["price_of_execution"] = (
            quote_price * fx_rate
        ).quantize(Decimal("0.000000000001"))

trades = []
for trade_id, execution in enumerate((row for row in executions if row["status"] == "FILLED"), start=1):
    trades.append({
        "trade_id": trade_id,
        "execution_id": execution["execution_id"],
        "account_id": execution["account_id"],
        "trade_date": execution["finished_at"],
        "settlement_date": execution["finished_at"] + timedelta(days=random.randint(1, 2)),
    })

transactions = []
transaction_types = ["DEPOSIT", "WITHDRAWAL", "DIVIDEND", "INTEREST", "FEE"]
for account_id in sorted(accounts_by_id):
    for _ in range(random.randint(1, 10)):
        transaction_date = datetime.now() - timedelta(days=random.randint(0, 90))
        status = random.choices(["COMPLETED", "PENDING", "FAILED"], weights=[80, 15, 5])[0]
        transactions.append({
            "transaction_id": len(transactions) + 1,
            "account_id": account_id,
            "client_request_id": str(uuid.uuid4()),
            "transaction_amount": round(random.uniform(100, 5000), 2),
            "transaction_type": random.choice(transaction_types),
            "transaction_date": transaction_date,
            "transaction_status": status,
            "completed_at": transaction_date + timedelta(minutes=random.randint(1, 30)) if status != "PENDING" else None,
            "failure_reason": "Insufficient Funds" if status == "FAILED" else None,
        })


with (data_folder / "executions_insert.sql").open("w", encoding="utf-8") as output:
    for row in executions:
        output.write(
            "INSERT INTO Executions (Execution_ID, Order_ID, Account_ID, Security_ID, Side, "
            "Status_Of_Execution, Started_At, Finished_At, Quote_Price, Quote_Currency, "
            "Quote_Timestamp, Quote_Source, FX_Rate_To_USD, FX_Quote_Timestamp, FX_Source, "
            "Quantity_Filled, Price_Of_Execution, Exchange_Trade_ID, Failure_Reason) VALUES ("
            + ", ".join(sql_value(row[key]) for key in (
                "execution_id", "order_id", "account_id", "security_id", "side", "status",
                "started_at", "finished_at", "quote_price", "quote_currency", "quote_timestamp",
                "quote_source", "fx_rate", "fx_quote_timestamp", "fx_source", "quantity_filled",
                "price_of_execution", "exchange_trade_id", "failure_reason",
            )) + ");\n"
        )

with (data_folder / "trades_insert.sql").open("w", encoding="utf-8") as output:
    for row in trades:
        output.write(
            "INSERT INTO Trades (Trade_ID, Execution_ID, Account_ID, Trade_Date, Settlement_Date) VALUES "
            f"({row['trade_id']}, {row['execution_id']}, {row['account_id']}, "
            f"'{row['trade_date']}', '{row['settlement_date']}');\n"
        )

with (data_folder / "transactions_insert.sql").open("w", encoding="utf-8") as output:
    for row in transactions:
        output.write(
            "INSERT INTO Transactions (Transaction_ID, Account_ID, Client_Request_ID, Transaction_Amount, "
            "Transaction_Type, Transaction_Date, Transaction_Status, Completed_At, Failure_Reason) VALUES ("
            + ", ".join(sql_value(row[key]) for key in (
                "transaction_id", "account_id", "client_request_id", "transaction_amount",
                "transaction_type", "transaction_date", "transaction_status", "completed_at", "failure_reason",
            )) + ");\n"
        )

cash_ledger = []
for account_id in sorted(accounts_by_id):
    running_balance = Decimal(str(account_cash_balances.get(account_id, 0.0)))
    completed_transactions = [row for row in transactions if row["account_id"] == account_id and row["transaction_status"] == "COMPLETED"]
    account_trades = [row for row in trades if row["account_id"] == account_id]
    entries = [(row["transaction_date"], "transaction", row) for row in completed_transactions]
    entries += [(row["trade_date"], "trade", row) for row in account_trades]

    for ledger_id, (_, kind, row) in enumerate(sorted(entries, key=lambda item: item[0]), start=len(cash_ledger) + 1):
        if kind == "transaction":
            amount = Decimal(str(row["transaction_amount"]))
            debit = amount if row["transaction_type"] in ("WITHDRAWAL", "FEE") else Decimal("0")
            credit = amount if row["transaction_type"] in ("DEPOSIT", "DIVIDEND", "INTEREST") else Decimal("0")
            entry_type = row["transaction_type"]
            transaction_id, trade_id, entry_date = row["transaction_id"], None, row["transaction_date"]
        else:
            execution = executions[row["execution_id"] - 1]
            amount = (
                Decimal(str(execution["quantity_filled"])) * execution["price_of_execution"]
            ).quantize(Decimal("0.01"))
            is_buy = execution["side"] == "B"
            debit, credit = (amount, Decimal("0")) if is_buy else (Decimal("0"), amount)
            entry_type = "BUY" if is_buy else "SELL"
            transaction_id, trade_id, entry_date = None, row["trade_id"], row["trade_date"]

        running_balance = max(Decimal("0"), running_balance - debit + credit)
        cash_ledger.append({
            "ledger_id": ledger_id, "account_id": account_id, "transaction_id": transaction_id,
            "trade_id": trade_id, "entry_type": entry_type, "debit": debit, "credit": credit,
            "running_balance": running_balance, "entry_date": entry_date,
        })

with (data_folder / "cash_ledger_insert.sql").open("w", encoding="utf-8") as output:
    for row in cash_ledger:
        output.write(
            "INSERT INTO Cash_Ledger (Ledger_ID, Account_ID, Transaction_ID, Trade_ID, Entry_Type, "
            "Debit_Amount, Credit_Amount, Running_Balance, Entry_Date) VALUES "
            f"({row['ledger_id']}, {row['account_id']}, {sql_value(row['transaction_id'])}, "
            f"{sql_value(row['trade_id'])}, '{row['entry_type']}', {row['debit']:.2f}, "
            f"{row['credit']:.2f}, {row['running_balance']:.2f}, '{row['entry_date']}');\n"
        )

print(f"Generated executions={len(executions)}, trades={len(trades)}, transactions={len(transactions)}, cash ledger={len(cash_ledger)}")