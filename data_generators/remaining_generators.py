#!/usr/bin/env python3
"""Generate account positions and disputes from generated trade data."""

import csv
import io
import random
import re
from datetime import datetime, timedelta
from pathlib import Path

data_folder = Path(__file__).parent.parent / "data"


def values_from_line(line):
    values_text = line.split("VALUES (", 1)[1].rsplit(");", 1)[0]
    return next(csv.reader(io.StringIO(values_text), quotechar="'", skipinitialspace=True))


executions = {}
with (data_folder / "executions_insert.sql").open(encoding="utf-8") as source:
    for line in source:
        if line.startswith("INSERT INTO Executions"):
            values = values_from_line(line)
            executions[int(values[0])] = {
                "account_id": int(values[2]),
                "security_id": int(values[3]),
                "status": values[5],
                "quantity": None if values[15] == "NULL" else float(values[15]),
                "price": None if values[16] == "NULL" else float(values[16]),
            }

trades = {}
with (data_folder / "trades_insert.sql").open(encoding="utf-8") as source:
    for line in source:
        if line.startswith("INSERT INTO Trades"):
            values = values_from_line(line)
            trades[int(values[0])] = {
                "execution_id": int(values[1]),
                "account_id": int(values[2]),
            }

transactions = {}
with (data_folder / "transactions_insert.sql").open(encoding="utf-8") as source:
    for line in source:
        if line.startswith("INSERT INTO Transactions"):
            values = values_from_line(line)
            transactions[int(values[0])] = {"account_id": int(values[1])}

admin_ids = []
with (data_folder / "users_insert.sql").open(encoding="utf-8") as source:
    for line in source:
        if not line.startswith("INSERT INTO Users"):
            continue
        values = values_from_line(line)
        if int(values[4]) == 2:
            admin_ids.append(int(values[0]))

positions = {}
for trade in trades.values():
    execution = executions.get(trade["execution_id"])
    if not execution or execution["quantity"] is None or execution["price"] is None:
        continue
    key = (trade["account_id"], execution["security_id"])
    position = positions.setdefault(key, {"quantity": 0.0, "cost": 0.0})
    position["quantity"] += execution["quantity"]
    position["cost"] += execution["quantity"] * execution["price"]

with (data_folder / "account_positions_insert.sql").open("w", encoding="utf-8") as output:
    position_id = 1
    for (account_id, security_id), position in positions.items():
        if position["quantity"] <= 0:
            continue
        average_price = position["cost"] / position["quantity"]
        output.write(
            "INSERT INTO Account_Positions (Position_ID, Account_ID, Security_ID, Quantity, "
            "Average_Price, Updated_Date) VALUES "
            f"({position_id}, {account_id}, {security_id}, {position['quantity']:.12f}, "
            f"{average_price:.12f}, '{datetime.now()}');\n"
        )
        position_id += 1

reference_ids = [("trade", key, row["account_id"]) for key, row in trades.items()]
reference_ids += [("transaction", key, row["account_id"]) for key, row in transactions.items()]
dispute_types = ["TRADE_MISMATCH", "SETTLEMENT_ERROR", "QUOTE_ERROR", "SYSTEM_ERROR", "UNAUTHORIZED_TRADE"]
dispute_statuses = ["OPEN", "UNDER_REVIEW", "ESCALATED", "RESOLVED", "REJECTED"]

with (data_folder / "disputes_insert.sql").open("w", encoding="utf-8") as output:
    for dispute_id, (reference_type, reference_id, account_id) in enumerate(
        random.sample(reference_ids, k=min(len(reference_ids) // 20, 1000)), start=1
    ):
        date_created = datetime.now() - timedelta(days=random.randint(0, 30))
        status = random.choice(dispute_statuses)
        date_resolved = (
            date_created + timedelta(days=random.randint(1, 5))
            if status in ("RESOLVED", "REJECTED") else None
        )
        transaction_id = reference_id if reference_type == "transaction" else None
        trade_id = reference_id if reference_type == "trade" else None
        admin_id = random.choice(admin_ids) if admin_ids else None
        resolved_sql = "NULL" if date_resolved is None else f"'{date_resolved}'"
        output.write(
            "INSERT INTO Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Trade_ID, "
            "Dispute_Type, Description, Status, Date_Created, Date_Resolved) VALUES "
            f"({dispute_id}, {account_id}, {admin_id if admin_id is not None else 'NULL'}, "
            f"{transaction_id if transaction_id is not None else 'NULL'}, "
            f"{trade_id if trade_id is not None else 'NULL'}, '{random.choice(dispute_types)}', "
            f"'Generated test dispute for integration validation', '{status}', '{date_created}', {resolved_sql});\n"
        )

print(f"Generated account positions={len(positions)}, disputes={min(len(reference_ids) // 20, 1000)}")