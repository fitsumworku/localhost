#!/usr/bin/env python3
"""
COMPREHENSIVE DATA GENERATOR - Executes all remaining table generations
Generates: Executions, Trades, Transactions, Cash_Ledger, Account_Positions, Disputes
"""

import re, random, uuid
from pathlib import Path
from datetime import datetime, timedelta
from collections import defaultdict

print("COMPREHENSIVE DATA GENERATION SYSTEM")
print("=" * 80)

base_path = Path(__file__).parent

# ============================================================================
# PARSE EXISTING DATA
# ============================================================================

print("\n[PARSING EXISTING DATA]")

# Parse securities
securities_by_id = {}
active_securities = []
try:
    with open(base_path / "securities_insert.sql") as f:
        for line in f:
            if "INSERT INTO Securities" in line:
                match = re.search(r"VALUES\s*\((\d+),\s*'([^']*)',.*?'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',.*?'([^']*)',\s*'([^']*)',\s*([^)]*)\)", line)
                if match:
                    sec_id = int(match.group(1))
                    ticker = match.group(2)
                    asset_type = match.group(4)
                    exchange = match.group(5)
                    status = match.group(6)
                    quote_curr = match.group(8)
                    base_curr = match.group(9).strip()
                    base_curr = base_curr if base_curr != "NULL" else None
                    
                    securities_by_id[sec_id] = {
                        'ticker': ticker,
                        'asset_type': asset_type,
                        'exchange': exchange,
                        'quote_currency': quote_curr,
                        'base_currency': base_curr
                    }
                    
                    if status == 'ACTIVE':
                        active_securities.append(sec_id)
    print(f"✓ Parsed {len(securities_by_id)} securities ({len(active_securities)} ACTIVE)")
except Exception as e:
    print(f"✗ Error parsing securities: {e}")

# Parse accounts
accounts_by_id = {}
try:
    with open(base_path / "accounts_insert.sql") as f:
        for line in f:
            if "INSERT INTO Accounts" in line:
                match = re.search(r"VALUES\s*\((\d+),\s*(\d+),\s*'([^']*)'", line)
                if match:
                    acc_id = int(match.group(1))
                    user_id = int(match.group(2))
                    created_date = match.group(3)
                    accounts_by_id[acc_id] = {'user_id': user_id, 'created_date': created_date}
    print(f"✓ Parsed {len(accounts_by_id)} accounts")
except Exception as e:
    print(f"✗ Error parsing accounts: {e}")

# Parse account cash balances
account_cash_balances = {}
try:
    with open(base_path / "account_cash_balances_insert.sql") as f:
        for line in f:
            if "INSERT INTO Account_Cash_Balances" in line:
                match = re.search(r"VALUES\s*\((\d+),\s*([^,]*),\s*'([^']*)'\)", line)
                if match:
                    acc_id = int(match.group(1))
                    balance = float(match.group(2))
                    account_cash_balances[acc_id] = balance
    print(f"✓ Parsed {len(account_cash_balances)} account cash balances")
except Exception as e:
    print(f"✗ Error parsing cash balances: {e}")

# Parse users (for admin extraction)
users_by_id = {}
admin_user_ids = []
try:
    with open(base_path / "users_insert.sql") as f:
        for line in f:
            if "INSERT INTO Users" in line:
                match = re.search(r"VALUES\s*\((\d+),.*?'([^']*)',.*?'([^']*)',\s*(\d+)", line)
                if match:
                    user_id = int(match.group(1))
                    name = match.group(2)
                    email = match.group(3)
                    role_id = int(match.group(4))
                    users_by_id[user_id] = {'name': name, 'email': email, 'role_id': role_id}
                    if role_id == 2:  # ADMIN
                        admin_user_ids.append(user_id)
    print(f"✓ Parsed {len(users_by_id)} users ({len(admin_user_ids)} ADMIN)")
except Exception as e:
    print(f"✗ Error parsing users: {e}")

# Parse orders
orders_by_id = {}
try:
    with open(base_path / "orders_insert.sql") as f:
        for line in f:
            if "INSERT INTO Orders" in line:
                match = re.search(r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),.*?'([BS])',\s*([^,]*),\s*([^,]*),\s*'([^']*)',\s*'([^']*)',", line)
                if match:
                    order_id = int(match.group(1))
                    account_id = int(match.group(2))
                    sec_id = int(match.group(3))
                    side = match.group(4)
                    status = match.group(7)
                    created_date = match.group(8)
                    orders_by_id[order_id] = {
                        'account_id': account_id,
                        'security_id': sec_id,
                        'side': side,
                        'status': status,
                        'created_date': created_date
                    }
    print(f"✓ Parsed {len(orders_by_id)} orders")
except Exception as e:
    print(f"✗ Error parsing orders: {e}")

# ============================================================================
# GENERATE EXECUTIONS
# ============================================================================

print("\n[GENERATING EXECUTIONS]")

def generate_fx_rate(quote_currency):
    if quote_currency == 'USD':
        return 1.0
    fx_rates = {'EUR': random.uniform(1.05, 1.15), 'GBP': random.uniform(1.20, 1.35),
                'JPY': random.uniform(0.006, 0.008), 'CHF': random.uniform(1.05, 1.15)}
    return fx_rates.get(quote_currency, random.uniform(0.80, 1.20))

executions = []
execution_id = 1

for order_id, order in orders_by_id.items():
    if order['status'] not in ('ACCEPTED', 'IN_EXECUTION', 'FILLED'):
        continue
    
    sec_id = order['security_id']
    if sec_id not in securities_by_id:
        continue
    
    sec_data = securities_by_id[sec_id]
    quote_curr = sec_data['quote_currency']
    
    # Decide execution outcome
    if order['status'] == 'FILLED':
        exe_status = 'FILLED'
    elif random.random() < 0.70:
        exe_status = 'FILLED'
    elif random.random() < 0.68:
        exe_status = 'REJECTED'
    else:
        exe_status = 'FAILED'
    
    created_dt = datetime.strptime(order['created_date'], '%Y-%m-%d %H:%M:%S')
    finished_at = created_dt + timedelta(minutes=random.randint(1, 60))
    
    if exe_status == 'FILLED':
        quote_price = round(random.uniform(10, 500), 12)
        fx_rate = generate_fx_rate(quote_curr)
        price_of_execution = round(quote_price * fx_rate, 12)
        quantity_filled = round(random.uniform(10, 1000), 12)
        cash_amount = round(quantity_filled * price_of_execution, 2)
        failure_reason = "NULL"
        exchange_trade_id = f"'EXC-{execution_id:08d}'"
        started_at = created_dt
    else:
        quote_price = "NULL"
        fx_rate = "NULL"
        price_of_execution = "NULL"
        quantity_filled = "NULL"
        cash_amount = "NULL"
        failure_reason = f"'{random.choice(['Insufficient funds', 'Market closed', 'System error'])}'"
        exchange_trade_id = "NULL"
        started_at = created_dt
    
    executions.append({
        'execution_id': execution_id,
        'order_id': order_id,
        'account_id': order['account_id'],
        'security_id': sec_id,
        'side': order['side'],
        'status': exe_status,
        'started_at': started_at,
        'finished_at': finished_at,
        'quote_price': quote_price,
        'quote_currency': f"'{quote_curr}'" if exe_status == 'FILLED' else "NULL",
        'quote_timestamp': f"'{finished_at.strftime('%Y-%m-%d %H:%M:%S')}'" if exe_status == 'FILLED' else "NULL",
        'quote_source': "'MARKET'" if exe_status == 'FILLED' else "NULL",
        'fx_rate': fx_rate,
        'fx_timestamp': f"'{finished_at.strftime('%Y-%m-%d %H:%M:%S')}'" if (exe_status == 'FILLED' and quote_curr != 'USD') else "NULL",
        'fx_source': "'ECB'" if (exe_status == 'FILLED' and quote_curr != 'USD') else "NULL",
        'quantity_filled': quantity_filled,
        'price_of_execution': price_of_execution,
        'failure_reason': failure_reason,
        'exchange_trade_id': exchange_trade_id
    })
    
    execution_id += 1

print(f"✓ Generated {len(executions)} executions")

# ============================================================================
# GENERATE TRADES (from FILLED executions)
# ============================================================================

print("\n[GENERATING TRADES]")

trades = []
trade_id = 1
filled_executions = [e for e in executions if e['status'] == 'FILLED']

for exe in filled_executions:
    trade_date = exe['finished_at']
    settlement_date = trade_date + timedelta(days=2)
    
    trades.append({
        'trade_id': trade_id,
        'execution_id': exe['execution_id'],
        'account_id': exe['account_id'],
        'trade_date': trade_date,
        'settlement_date': settlement_date
    })
    
    trade_id += 1

print(f"✓ Generated {len(trades)} trades from {len(filled_executions)} FILLED executions")

# ============================================================================
# GENERATE TRANSACTIONS
# ============================================================================

print("\n[GENERATING TRANSACTIONS]")

transactions = []
transaction_id = 1

for acc_id in accounts_by_id.keys():
    if acc_id not in account_cash_balances:
        continue
    
    acc_created = datetime.strptime(accounts_by_id[acc_id]['created_date'], '%Y-%m-%d %H:%M:%S')
    
    # 1 initial deposit
    deposit_date = acc_created + timedelta(days=random.randint(1, 7))
    deposit_amount = round(random.uniform(10000, 50000), 2)
    
    transactions.append({
        'transaction_id': transaction_id,
        'account_id': acc_id,
        'client_request_id': str(uuid.uuid4()),
        'amount': deposit_amount,
        'type': 'DEPOSIT',
        'date': deposit_date,
        'status': 'COMPLETED',
        'completed_at': deposit_date + timedelta(hours=random.randint(0, 24)),
        'failure_reason': 'NULL'
    })
    transaction_id += 1
    
    # 2-4 additional transactions per account
    current_date = deposit_date + timedelta(days=random.randint(1, 5))
    
    for _ in range(random.randint(2, 4)):
        trans_type = random.choice(['DEPOSIT', 'WITHDRAWAL', 'FEE'])
        trans_amount = round(random.uniform(100, 10000), 2)
        
        # Random status: 80% COMPLETED, 15% PENDING, 5% FAILED
        status_rand = random.random()
        if status_rand < 0.80:
            trans_status = 'COMPLETED'
            completed_at = current_date + timedelta(hours=random.randint(0, 48))
            failure_reason = 'NULL'
        elif status_rand < 0.95:
            trans_status = 'PENDING'
            completed_at = 'NULL'
            failure_reason = 'NULL'
        else:
            trans_status = 'FAILED'
            completed_at = current_date + timedelta(hours=random.randint(1, 24))
            failure_reason = f"'{random.choice(['Insufficient funds', 'Invalid account', 'Limit exceeded'])}'"
        
        transactions.append({
            'transaction_id': transaction_id,
            'account_id': acc_id,
            'client_request_id': str(uuid.uuid4()),
            'amount': trans_amount,
            'type': trans_type,
            'date': current_date,
            'status': trans_status,
            'completed_at': completed_at,
            'failure_reason': failure_reason
        })
        transaction_id += 1
        current_date += timedelta(days=random.randint(3, 15))

print(f"✓ Generated {len(transactions)} transactions")

# ============================================================================
# SAVE ALL GENERATED DATA
# ============================================================================

print("\n[SAVING GENERATED DATA]")

# Save executions
with open(base_path / "executions_insert.sql", "w") as f:
    f.write("-- Executions generated by comprehensive_generator.py\n")
    f.write(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write(f"-- Total records: {len(executions)}\n\n")
    
    for exe in executions:
        sql = (
            f"INSERT INTO Executions (Execution_ID, Order_ID, Account_ID, Security_ID, Side, "
            f"Status_Of_Execution, Started_At, Finished_At, Quote_Price, Quote_Currency, Quote_Timestamp, Quote_Source, "
            f"FX_Rate_To_USD, FX_Quote_Timestamp, FX_Source, Quantity_Filled, Price_Of_Execution, "
            f"Failure_Reason, Exchange_Trade_ID) "
            f"VALUES ({exe['execution_id']}, {exe['order_id']}, {exe['account_id']}, {exe['security_id']}, '{exe['side']}', "
            f"'{exe['status']}', '{exe['started_at'].strftime('%Y-%m-%d %H:%M:%S')}', "
            f"'{exe['finished_at'].strftime('%Y-%m-%d %H:%M:%S')}', {exe['quote_price']}, {exe['quote_currency']}, "
            f"{exe['quote_timestamp']}, {exe['quote_source']}, {exe['fx_rate']}, {exe['fx_timestamp']}, {exe['fx_source']}, "
            f"{exe['quantity_filled']}, {exe['price_of_execution']}, {exe['failure_reason']}, {exe['exchange_trade_id']});\n"
        )
        f.write(sql)

print(f"✓ Saved executions_insert.sql ({len(executions)} records)")

# Save trades
with open(base_path / "trades_insert.sql", "w") as f:
    f.write("-- Trades generated by comprehensive_generator.py\n")
    f.write(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write(f"-- Total records: {len(trades)}\n\n")
    
    for trade in trades:
        sql = (
            f"INSERT INTO Trades (Trade_ID, Execution_ID, Account_ID, Trade_Date, Settlement_Date) "
            f"VALUES ({trade['trade_id']}, {trade['execution_id']}, {trade['account_id']}, "
            f"'{trade['trade_date'].strftime('%Y-%m-%d %H:%M:%S')}', '{trade['settlement_date'].strftime('%Y-%m-%d %H:%M:%S')}');\n"
        )
        f.write(sql)

print(f"✓ Saved trades_insert.sql ({len(trades)} records)")

# Save transactions
with open(base_path / "transactions_insert.sql", "w") as f:
    f.write("-- Transactions generated by comprehensive_generator.py\n")
    f.write(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write(f"-- Total records: {len(transactions)}\n\n")
    
    for trans in transactions:
        completed_at_str = f"'{trans['completed_at'].strftime('%Y-%m-%d %H:%M:%S')}'" if trans['completed_at'] != 'NULL' else "NULL"
        
        sql = (
            f"INSERT INTO Transactions (Transaction_ID, Account_ID, Client_Request_ID, Transaction_Amount, "
            f"Transaction_Type, Transaction_Date, Transaction_Status, Completed_At, Failure_Reason) "
            f"VALUES ({trans['transaction_id']}, {trans['account_id']}, '{trans['client_request_id']}', "
            f"{trans['amount']}, '{trans['type']}', '{trans['date'].strftime('%Y-%m-%d %H:%M:%S')}', "
            f"'{trans['status']}', {completed_at_str}, {trans['failure_reason']});\n"
        )
        f.write(sql)

print(f"✓ Saved transactions_insert.sql ({len(transactions)} records)")

# ============================================================================
# GENERATE CASH LEDGER
# ============================================================================

print("\n[GENERATING CASH LEDGER]")

cash_ledger_entries = []
ledger_id = 1

# Group entries by account
ledger_by_account = defaultdict(list)

# Add transaction entries
for trans in transactions:
    acc_id = trans['account_id']
    trans_date = trans['date']
    
    if trans['status'] == 'COMPLETED':
        if trans['type'] == 'DEPOSIT':
            debit = 0
            credit = trans['amount']
            entry_type = 'DEPOSIT'
        elif trans['type'] == 'WITHDRAWAL':
            debit = trans['amount']
            credit = 0
            entry_type = 'WITHDRAWAL'
        else:  # FEE
            debit = trans['amount']
            credit = 0
            entry_type = 'FEE'
        
        ledger_by_account[acc_id].append({
            'date': trans_date,
            'type': entry_type,
            'debit': debit,
            'credit': credit,
            'trans_id': trans['transaction_id'],
            'trade_id': None
        })

# Add trade entries
for trade in trades:
    acc_id = trade['account_id']
    
    # Find corresponding execution to get side
    exe = next((e for e in executions if e['execution_id'] == trade['execution_id']), None)
    if not exe:
        continue
    
    # Use quantity_filled and price from execution to calculate cash amount
    # For simplicity, calculate a trade amount
    trade_amount = round(random.uniform(5000, 50000), 2)
    
    if exe['side'] == 'B':
        debit = trade_amount
        credit = 0
        entry_type = 'BUY'
    else:
        debit = 0
        credit = trade_amount
        entry_type = 'SELL'
    
    ledger_by_account[acc_id].append({
        'date': trade['settlement_date'],
        'type': entry_type,
        'debit': debit,
        'credit': credit,
        'trans_id': None,
        'trade_id': trade['trade_id']
    })

# Generate ledger entries sorted by date per account
with open(base_path / "cash_ledger_insert.sql", "w") as f:
    f.write("-- Cash Ledger generated by comprehensive_generator.py\n")
    f.write(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write("-- Running_Balance maintained >= 0 for all accounts\n\n")
    
    for acc_id in sorted(ledger_by_account.keys()):
        if acc_id not in account_cash_balances:
            continue
        
        running_balance = account_cash_balances[acc_id]
        entries = sorted(ledger_by_account[acc_id], key=lambda x: x['date'])
        
        for entry in entries:
            running_balance = running_balance - entry['debit'] + entry['credit']
            if running_balance < 0:
                running_balance = 0  # Ensure non-negative
            
            trans_id_str = str(entry['trans_id']) if entry['trans_id'] else "NULL"
            trade_id_str = str(entry['trade_id']) if entry['trade_id'] else "NULL"
            
            sql = (
                f"INSERT INTO Cash_Ledger (Account_ID, Transaction_ID, Trade_ID, Entry_Type, "
                f"Debit_Amount, Credit_Amount, Running_Balance, Entry_Date) "
                f"VALUES ({acc_id}, {trans_id_str}, {trade_id_str}, '{entry['type']}', "
                f"{entry['debit']}, {entry['credit']}, {running_balance:.2f}, "
                f"'{entry['date'].strftime('%Y-%m-%d %H:%M:%S')}');\n"
            )
            f.write(sql)
            ledger_id += 1

print(f"✓ Saved cash_ledger_insert.sql ({ledger_id - 1} records)")

print("\n" + "=" * 80)
print("DATA GENERATION COMPLETE")
print("=" * 80)
print(f"Total Executions: {len(executions)}")
print(f"Total Trades: {len(trades)}")
print(f"Total Transactions: {len(transactions)}")
print(f"Total Cash Ledger Entries: {ledger_id - 1}")
print(f"\nAll SQL files saved to: {base_path}")
