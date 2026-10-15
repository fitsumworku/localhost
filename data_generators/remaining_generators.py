#!/usr/bin/env python3
"""
Remaining generators for: Account_Positions, Disputes
"""

import re, random, uuid
from pathlib import Path
from datetime import datetime, timedelta
from collections import defaultdict

print("=" * 80)
print("GENERATING REMAINING TABLES: Account_Positions, Disputes")
print("=" * 80)

base_path = Path(__file__).parent
data_folder = Path(__file__).parent.parent / "data"

# Parse trades
print("\n[PARSING TRADES]")
trades_by_id = {}
try:
    with open(data_folder / "trades_insert.sql") as f:
        content = f.read()
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*([\d.]+),"
        matches = re.findall(pattern, content)
        for match in matches:
            trade_id = int(match[0])
            exec_id = int(match[1])
            account_id = int(match[2])
            security_id = int(match[3])
            quantity = float(match[4])
            trades_by_id[trade_id] = {
                'execution_id': exec_id,
                'account_id': account_id,
                'security_id': security_id,
                'quantity': quantity
            }
    print(f"[OK] Parsed {len(trades_by_id)} trades")
except Exception as e:
    print(f"[ERR] Error parsing trades: {e}")

# Parse executions
print("[PARSING EXECUTIONS]")
executions_by_id = {}
try:
    with open(data_folder / "executions_insert.sql") as f:
        content = f.read()
        pattern = r"VALUES\s*\((\d+),.*?'(FILLED|REJECTED|FAILED)',"
        matches = re.findall(pattern, content)
        for match in matches:
            exec_id = int(match[0])
            status = match[1]
            if status == 'FILLED':
                executions_by_id[exec_id] = status
    print(f"[OK] Parsed {len(executions_by_id)} FILLED executions")
except Exception as e:
    print(f"[ERR] Error parsing executions: {e}")

# Parse securities with security_id
print("[PARSING SECURITIES]")
securities_by_id = {}
try:
    with open(data_folder / "securities_insert.sql") as f:
        content = f.read()
        pattern = r"VALUES\s*\((\d+),\s*'([^']*)',.*?'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*([^,]*),\s*'([^']*)',"
        matches = re.findall(pattern, content)
        for match in matches:
            security_id = int(match[0])
            ticker = match[1]
            asset_type = match[2]
            exchange = match[3]
            quote_currency = match[4]
            securities_by_id[security_id] = {
                'ticker': ticker,
                'asset_type': asset_type,
                'exchange': exchange,
                'quote_currency': quote_currency
            }
    print(f"[OK] Parsed {len(securities_by_id)} securities")
except Exception as e:
    print(f"[ERR] Error parsing securities: {e}")

# Parse orders
print("[PARSING ORDERS]")
orders_by_id = {}
try:
    with open(data_folder / "orders_insert.sql") as f:
        content = f.read()
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),"
        matches = re.findall(pattern, content)
        for match in matches:
            order_id = int(match[0])
            acc_id = int(match[1])
            sec_id = int(match[2])
            orders_by_id[order_id] = {'account_id': acc_id, 'security_id': sec_id}
    print(f"[OK] Parsed {len(orders_by_id)} orders")
except Exception as e:
    print(f"[ERR] Error parsing orders: {e}")

# Parse transactions
print("[PARSING TRANSACTIONS]")
transactions_by_id = {}
try:
    with open(data_folder / "transactions_insert.sql") as f:
        content = f.read()
        pattern = r"VALUES\s*\((\d+),\s*(\d+),"
        matches = re.findall(pattern, content)
        for match in matches:
            trans_id = int(match[0])
            acc_id = int(match[1])
            transactions_by_id[trans_id] = {'account_id': acc_id}
    print(f"[OK] Parsed {len(transactions_by_id)} transactions")
except Exception as e:
    print(f"[ERR] Error parsing transactions: {e}")

# Parse users
print("[PARSING USERS]")
admin_ids = []
try:
    with open(data_folder / "users_insert.sql") as f:
        content = f.read()
        pattern = r"VALUES\s*\((\d+),.*?(\d+)\)"
        matches = re.findall(pattern, content)
        for match in matches:
            user_id = int(match[0])
            role_id = int(match[1])
            if role_id == 2:
                admin_ids.append(user_id)
    print(f"[OK] Parsed {len(admin_ids)} admin users")
except Exception as e:
    print(f"[ERR] Error parsing users: {e}")

print("\n[GENERATING ACCOUNT POSITIONS]")

positions = {}
position_id = 1
position_count = 0

for trade_id, trade in trades_by_id.items():
    account_id = trade['account_id']
    security_id = trade['security_id']
    quantity = trade['quantity']
    
    key = (account_id, security_id)
    
    if key not in positions:
        positions[key] = {
            'quantity': 0,
            'cost_basis': 0,
            'current_value': 0,
        }
    
    positions[key]['quantity'] += quantity
    positions[key]['cost_basis'] += quantity * random.uniform(10, 500)

account_positions = []
for (account_id, security_id), pos_data in positions.items():
    if pos_data['quantity'] > 0:
        account_positions.append({
            'position_id': position_id,
            'account_id': account_id,
            'security_id': security_id,
            'quantity': pos_data['quantity'],
            'cost_basis': pos_data['cost_basis'],
            'current_value': pos_data['cost_basis'] * random.uniform(0.9, 1.1),
            'last_updated': datetime.now(),
        })
        position_id += 1
        position_count += 1

print(f"[OK] Generated {position_count} account positions (>0 quantity only)")

print("\n[GENERATING DISPUTES]")

disputes = []
dispute_id = 1
dispute_types = ['TRADE_MISMATCH', 'SETTLEMENT_ERROR', 'QUOTE_ERROR', 'SYSTEM_ERROR', 'UNAUTHORIZED_TRADE']
dispute_statuses = ['OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED']

for trade_id in random.sample(list(trades_by_id.keys()), k=min(len(trades_by_id) // 20, 1000)):
    trade = trades_by_id[trade_id]
    
    dispute = {
        'dispute_id': dispute_id,
        'trade_id': trade_id,
        'account_id': trade['account_id'],
        'dispute_type': random.choice(dispute_types),
        'status': random.choice(dispute_statuses),
        'reported_at': datetime.now() - timedelta(days=random.randint(0, 30)),
        'resolved_at': datetime.now() if random.random() < 0.6 else None,
        'investigator_id': random.choice(admin_ids) if admin_ids else 1,
    }
    
    disputes.append(dispute)
    dispute_id += 1

print(f"[OK] Generated {len(disputes)} disputes")

print("\n[SAVING GENERATED DATA]")

# Save account positions
with open(data_folder / "account_positions_insert.sql", "w") as f:
    for pos in account_positions:
        f.write(f"INSERT INTO Account_Positions (Position_ID, Account_ID, Security_ID, Quantity, Cost_Basis, Current_Value, Last_Updated) VALUES ({pos['position_id']}, {pos['account_id']}, {pos['security_id']}, {pos['quantity']}, {pos['cost_basis']}, {pos['current_value']}, '{pos['last_updated']}');\n")

# Save disputes
with open(data_folder / "disputes_insert.sql", "w") as f:
    for disp in disputes:
        resolved = f"'{disp['resolved_at']}'" if disp['resolved_at'] else "NULL"
        f.write(f"INSERT INTO Disputes (Dispute_ID, Trade_ID, Account_ID, Dispute_Type, Status, Reported_At, Resolved_At, Investigator_ID) VALUES ({disp['dispute_id']}, {disp['trade_id']}, {disp['account_id']}, '{disp['dispute_type']}', '{disp['status']}', '{disp['reported_at']}', {resolved}, {disp['investigator_id']});\n")

print("\n" + "=" * 80)
print("ACCOUNT POSITIONS & DISPUTES GENERATION COMPLETE")
print("=" * 80)
print(f"Account Positions: {position_count}")
print(f"Disputes: {len(disputes)}")
