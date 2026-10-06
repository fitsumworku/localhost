#!/usr/bin/env python3
"""
REMAINING GENERATORS - Account Positions and Disputes
"""

import re, random, uuid
from pathlib import Path
from datetime import datetime, timedelta
from collections import defaultdict

base_path = Path(__file__).parent
data_folder = Path(__file__).parent.parent / "data"

print("=" * 80)
print("GENERATING REMAINING TABLES: Account_Positions, Disputes")
print("=" * 80)

# Parse trades from data folder
print("\n[PARSING TRADES]")
trades_by_id = {}
try:
    with open(data_folder / "trades_insert.sql") as f:
        for line in f:
            if "INSERT INTO Trades" in line:
                match = re.search(r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*'([^']*)',", line)
                if match:
                    trade_id = int(match.group(1))
                    exe_id = int(match.group(2))
                    acc_id = int(match.group(3))
                    trades_by_id[trade_id] = {'execution_id': exe_id, 'account_id': acc_id}
    print(f"âœ“ Parsed {len(trades_by_id)} trades")
except Exception as e:
    print(f"âœ— Error parsing trades: {e}")

# Parse executions for side and quantity
print("[PARSING EXECUTIONS]")
executions_by_id = {}
try:
    with open(data_folder / "executions_insert.sql") as f:
        for line in f:
            if "INSERT INTO Executions" in line:
                match = re.search(r"VALUES\s*\((\d+),.*?'([BS])',.*?'FILLED',.*?(\d+\.\d+),.*?(\d+\.\d+),", line)
                if match:
                    exe_id = int(match.group(1))
                    side = match.group(2)
                    quantity = float(match.group(3))
                    price = float(match.group(4))
                    executions_by_id[exe_id] = {'side': side, 'quantity': quantity, 'price': price}
    print(f"âœ“ Parsed {len(executions_by_id)} FILLED executions")
except Exception as e:
    print(f"âœ— Error parsing executions: {e}")

# Parse securities
print("[PARSING SECURITIES]")
securities_by_id = {}
ticker_counter = 1
try:
    with open(data_folder / "securities_insert.sql") as f:
        content = f.read()
        # Parse multi-line INSERT statements
        pattern = r"VALUES\s*\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*([^,]*),\s*'([^']*)',\s*([^)]*)\)"
        matches = re.findall(pattern, content)
        for match in matches:
            securities_by_id[ticker_counter] = match[0]  # Store ticker by assigned ID
            ticker_counter += 1
    print(f"âœ“ Parsed {len(securities_by_id)} securities")
except Exception as e:
    print(f"âœ— Error parsing securities: {e}")

# Parse orders
print("[PARSING ORDERS]")
orders_by_id = {}
try:
    with open(data_folder / "orders_insert.sql") as f:
        content = f.read()
        # Parse multi-line INSERT statements: VALUES (order_id, account_id, security_id, ...)
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),"
        matches = re.findall(pattern, content)
        for match in matches:
            order_id = int(match[0])
            acc_id = int(match[1])
            sec_id = int(match[2])
            orders_by_id[order_id] = {'account_id': acc_id, 'security_id': sec_id}
    print(f"âœ“ Parsed {len(orders_by_id)} orders")
except Exception as e:
    print(f"âœ— Error parsing orders: {e}")

# Parse transactions
print("[PARSING TRANSACTIONS]")
transactions_by_id = {}
try:
    with open(data_folder / "transactions_insert.sql") as f:
        content = f.read()
        # Parse multi-line INSERT statements: VALUES (trans_id, account_id, ...)
        pattern = r"VALUES\s*\((\d+),\s*(\d+),"
        matches = re.findall(pattern, content)
        for match in matches:
            trans_id = int(match[0])
            acc_id = int(match[1])
            transactions_by_id[trans_id] = {'account_id': acc_id}
    print(f"âœ“ Parsed {len(transactions_by_id)} transactions")
except Exception as e:
    print(f"âœ— Error parsing transactions: {e}")

# Parse users
print("[PARSING USERS]")
admin_ids = []
try:
    with open(data_folder / "users_insert.sql") as f:
        content = f.read()
        # Parse multi-line INSERT statements: VALUES (user_id, ..., role_id)
        pattern = r"VALUES\s*\((\d+),.*?(\d+)\)"
        matches = re.findall(pattern, content)
        for match in matches:
            user_id = int(match[0])
            role_id = int(match[1])
            if role_id == 2:  # ADMIN
                admin_ids.append(user_id)
    print(f"âœ“ Parsed {len(admin_ids)} admin users")
except Exception as e:
    print(f"âœ— Error parsing users: {e}")

if not admin_ids:
    admin_ids = [1]  # fallback

# ============================================================================
# GENERATE ACCOUNT POSITIONS
# ============================================================================

print("\n[GENERATING ACCOUNT POSITIONS]")

positions = defaultdict(lambda: defaultdict(lambda: {'quantity': 0, 'cost_basis': 0, 'updated_date': None}))

for trade_id, trade_data in trades_by_id.items():
    exe_id = trade_data['execution_id']
    acc_id = trade_data['account_id']
    
    if exe_id not in executions_by_id:
        continue
    
    exe_data = executions_by_id[exe_id]
    side = exe_data['side']
    quantity = exe_data['quantity']
    price = exe_data['price']
    
    # Find the order to get security_id
    # Need to search for order linked to this execution
    order_id = None
    for oid, odata in orders_by_id.items():
        if odata['account_id'] == acc_id:
            order_id = oid
            break
    
    if not order_id or order_id not in orders_by_id:
        continue
    
    sec_id = orders_by_id[order_id]['security_id']
    
    pos = positions[acc_id][sec_id]
    
    if side == 'B':  # BUY
        # Update average cost
        total_cost = pos['cost_basis'] * pos['quantity'] + price * quantity
        pos['quantity'] += quantity
        if pos['quantity'] > 0:
            pos['cost_basis'] = total_cost / pos['quantity']
    else:  # SELL
        pos['quantity'] -= quantity
    
    pos['updated_date'] = datetime.now() - timedelta(days=random.randint(0, 30))

# Write account positions (exclude positions with quantity <= 0)
position_count = 0
with open(data_folder / "account_positions_insert.sql", "w") as f:
    f.write("-- Account Positions generated by remaining_generators.py\n")
    f.write(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write("-- Positions with Quantity > 0 only (closed positions excluded)\n\n")
    
    pos_id = 1
    for acc_id in sorted(positions.keys()):
        for sec_id in sorted(positions[acc_id].keys()):
            pos = positions[acc_id][sec_id]
            
            # Only include positions with positive quantity
            if pos['quantity'] > 0:
                sql = (
                    f"INSERT INTO Account_Positions (Account_ID, Security_ID, Quantity, Average_Price, Updated_Date) "
                    f"VALUES ({acc_id}, {sec_id}, {pos['quantity']:.12f}, {pos['cost_basis']:.12f}, "
                    f"'{pos['updated_date'].strftime('%Y-%m-%d %H:%M:%S')}');\n"
                )
                f.write(sql)
                position_count += 1

print(f"âœ“ Generated {position_count} account positions (>0 quantity only)")

# ============================================================================
# GENERATE DISPUTES
# ============================================================================

print("[GENERATING DISPUTES]")

disputes = []
dispute_id = 1

# Sample 5-10% of transactions and trades for disputes
sampled_transactions = random.sample(list(transactions_by_id.items()), 
                                    min(len(transactions_by_id), max(1, len(transactions_by_id) // 10)))
sampled_trades = random.sample(list(trades_by_id.items()), 
                              min(len(trades_by_id), max(1, len(trades_by_id) // 10)))

dispute_types = ['UNAUTHORIZED_TRANSACTION', 'INCORRECT_AMOUNT', 'INCORRECT_EXECUTION', 'SYSTEM_ERROR', 'BILLING_ERROR']
statuses = ['OPEN', 'UNDER_REVIEW', 'ESCALATED', 'RESOLVED', 'REJECTED']

for trans_id, trans_data in sampled_transactions:
    if random.random() > 0.3:  # 30% of sampled become disputes
        continue
    
    acc_id = trans_data['account_id']
    status = random.choice(statuses)
    dispute_type = random.choice(dispute_types)
    admin_id = random.choice(admin_ids) if admin_ids else 1
    
    date_created = datetime.now() - timedelta(days=random.randint(1, 60))
    date_resolved = None
    if status in ('RESOLVED', 'REJECTED'):
        date_resolved = date_created + timedelta(days=random.randint(7, 60))
    
    disputes.append({
        'dispute_id': dispute_id,
        'account_id': acc_id,
        'admin_id': admin_id,
        'trans_id': trans_id,
        'trade_id': None,
        'type': dispute_type,
        'status': status,
        'date_created': date_created,
        'date_resolved': date_resolved
    })
    dispute_id += 1

for trade_id, trade_data in sampled_trades:
    if random.random() > 0.3:
        continue
    
    acc_id = trade_data['account_id']
    status = random.choice(statuses)
    dispute_type = random.choice(dispute_types)
    admin_id = random.choice(admin_ids) if admin_ids else 1
    
    date_created = datetime.now() - timedelta(days=random.randint(1, 60))
    date_resolved = None
    if status in ('RESOLVED', 'REJECTED'):
        date_resolved = date_created + timedelta(days=random.randint(7, 60))
    
    disputes.append({
        'dispute_id': dispute_id,
        'account_id': acc_id,
        'admin_id': admin_id,
        'trans_id': None,
        'trade_id': trade_id,
        'type': dispute_type,
        'status': status,
        'date_created': date_created,
        'date_resolved': date_resolved
    })
    dispute_id += 1

# Write disputes
with open(data_folder / "disputes_insert.sql", "w") as f:
    f.write("-- Disputes generated by remaining_generators.py\n")
    f.write(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write(f"-- Total records: {len(disputes)}\n\n")
    
    for disp in disputes:
        trans_id_str = str(disp['trans_id']) if disp['trans_id'] else "NULL"
        trade_id_str = str(disp['trade_id']) if disp['trade_id'] else "NULL"
        date_resolved_str = f"'{disp['date_resolved'].strftime('%Y-%m-%d %H:%M:%S')}'" if disp['date_resolved'] else "NULL"
        
        sql = (
            f"INSERT INTO Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Trade_ID, "
            f"Dispute_Type, Status, Date_Created, Date_Resolved) "
            f"VALUES ({disp['dispute_id']}, {disp['account_id']}, {disp['admin_id']}, "
            f"{trans_id_str}, {trade_id_str}, '{disp['type']}', '{disp['status']}', "
            f"'{disp['date_created'].strftime('%Y-%m-%d %H:%M:%S')}', {date_resolved_str});\n"
        )
        f.write(sql)

print(f"âœ“ Generated {len(disputes)} disputes")

print("\n" + "=" * 80)
print("REMAINING GENERATORS COMPLETE")
print("=" * 80)
print(f"Account Positions: {position_count}")
print(f"Disputes: {len(disputes)}")

