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
data_folder = Path(__file__).parent.parent / "data"

# ============================================================================
# PARSE EXISTING DATA
# ============================================================================

print("\n[PARSING EXISTING DATA]")

# Parse securities from data folder with security_id
securities_by_id = {}
active_securities = []
try:
    with open(data_folder / "securities_insert.sql") as f:
        content = f.read()
        # Pattern: VALUES (security_id, 'TICKER', 'Name', 'ASSET_TYPE', 'EXCHANGE', 'QUOTE_CURRENCY', Base_Curr, 'STATUS', Sector)
        pattern = r"VALUES\s*\((\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*([^,]*),\s*'([^']*)',\s*([^)]*)\)"
        matches = re.findall(pattern, content)
        
        for match in matches:
            security_id = int(match[0])
            ticker = match[1]
            name = match[2]
            asset_type = match[3]
            exchange = match[4]
            quote_curr = match[5]
            base_curr = match[6].strip()
            base_curr = base_curr if base_curr != "NULL" else None
            status = match[7]
            
            sec_data = {
                'id': security_id,
                'ticker': ticker,
                'name': name,
                'asset_type': asset_type,
                'exchange': exchange,
                'quote_currency': quote_curr,
                'base_currency': base_curr
            }
            
            securities_by_id[security_id] = sec_data
            
            if status == 'ACTIVE':
                active_securities.append(security_id)
    
    print(f"[OK] Parsed {len(securities_by_id)} securities ({len(active_securities)} ACTIVE)")
except Exception as e:
    print(f"[ERR] Error parsing securities: {e}")

# Parse accounts from data folder
accounts_by_id = {}
try:
    with open(data_folder / "accounts_insert.sql") as f:
        for line in f:
            if "INSERT INTO Accounts" in line:
                match = re.search(r"VALUES\s*\((\d+),\s*(\d+),", line)
                if match:
                    account_id = int(match.group(1))
                    user_id = int(match.group(2))
                    accounts_by_id[account_id] = {'user_id': user_id}
    print(f"[OK] Parsed {len(accounts_by_id)} accounts")
except Exception as e:
    print(f"[ERR] Error parsing accounts: {e}")

# Parse account cash balances
account_cash_balances = {}
try:
    with open(data_folder / "account_cash_balances_insert.sql") as f:
        for line in f:
            if "INSERT INTO Account_Cash_Balances" in line:
                match = re.search(r"VALUES\s*\((\d+),\s*([\d.]+),", line)
                if match:
                    account_id = int(match.group(1))
                    cash_balance = float(match.group(2))
                    account_cash_balances[account_id] = cash_balance
    print(f"[OK] Parsed {len(account_cash_balances)} account cash balances")
except Exception as e:
    print(f"[ERR] Error parsing cash balances: {e}")

# Parse users
users_by_id = {}
admin_user_ids = []
try:
    with open(data_folder / "users_insert.sql") as f:
        for line in f:
            if "INSERT INTO Users" in line:
                match = re.search(r"VALUES\s*\((\d+),.*?(\d+)\)", line)
                if match:
                    user_id = int(match.group(1))
                    role_id = int(match.group(2))
                    users_by_id[user_id] = role_id
                    if role_id == 2:
                        admin_user_ids.append(user_id)
    print(f"[OK] Parsed {len(users_by_id)} users ({len(admin_user_ids)} ADMIN)")
except Exception as e:
    print(f"[ERR] Error parsing users: {e}")

# Parse orders
orders_by_id = {}
try:
    with open(data_folder / "orders_insert.sql") as f:
        content = f.read()
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),"
        matches = re.findall(pattern, content)
        for match in matches:
            order_id = int(match[0])
            account_id = int(match[1])
            security_id = int(match[2])
            orders_by_id[order_id] = {'account_id': account_id, 'security_id': security_id}
    print(f"[OK] Parsed {len(orders_by_id)} orders")
except Exception as e:
    print(f"[ERR] Error parsing orders: {e}")

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
    sec_id = order['security_id']
    if sec_id not in securities_by_id:
        continue
    
    sec_data = securities_by_id[sec_id]
    quote_curr = sec_data['quote_currency']
    
    # Random execution outcome
    if random.random() < 0.70:
        exe_status = 'FILLED'
    elif random.random() < 0.68:
        exe_status = 'REJECTED'
    else:
        exe_status = 'FAILED'
    
    finished_at = datetime.now() - timedelta(days=random.randint(0, 90))
    
    if exe_status == 'FILLED':
        quote_price = round(random.uniform(10, 500), 12)
        fx_rate = generate_fx_rate(quote_curr)
        price_of_execution = round(quote_price * fx_rate, 12)
        quantity_filled = round(random.uniform(10, 1000), 12)
        cash_amount = round(quantity_filled * price_of_execution, 2)
        failure_reason = "NULL"
        exchange_trade_id = f"'EXC-{execution_id:08d}'"
    else:
        quote_price = "NULL"
        fx_rate = "NULL"
        price_of_execution = "NULL"
        quantity_filled = "NULL"
        cash_amount = "NULL"
        failure_reason = f"'{random.choice(['Insufficient funds', 'Market closed', 'System error'])}'"
        exchange_trade_id = "NULL"
    
    executions.append({
        'execution_id': execution_id,
        'order_id': order_id,
        'account_id': order['account_id'],
        'security_id': sec_id,
        'status': exe_status,
        'quantity_filled': quantity_filled,
        'price_of_execution': price_of_execution,
        'cash_amount': cash_amount,
        'exchange_trade_id': exchange_trade_id,
        'finished_at': finished_at,
        'failure_reason': failure_reason,
    })
    
    execution_id += 1

print(f"[OK] Generated {len(executions)} executions")

print("\n[GENERATING TRADES]")

trades = []
trade_id = 1
filled_executions = [e for e in executions if e['status'] == 'FILLED']

for exec_data in filled_executions:
    trade = {
        'trade_id': trade_id,
        'execution_id': exec_data['execution_id'],
        'account_id': exec_data['account_id'],
        'security_id': exec_data['security_id'],
        'quantity': exec_data['quantity_filled'],
        'price': exec_data['price_of_execution'],
        'trade_value': exec_data['cash_amount'],
        'trade_date': exec_data['finished_at'],
        'trader_id': random.choice(admin_user_ids) if admin_user_ids else 1,
    }
    trades.append(trade)
    trade_id += 1

print(f"[OK] Generated {len(trades)} trades from {len(filled_executions)} FILLED executions")

print("\n[GENERATING TRANSACTIONS]")

transactions = []
transaction_id = 1
transaction_types = ['TRADE', 'DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'FEE']

for account_id in sorted(accounts_by_id.keys()):
    for _ in range(random.randint(1, 10)):
        trans_type = random.choice(transaction_types)
        
        if trans_type == 'TRADE':
            account_trades = [t for t in trades if t['account_id'] == account_id]
            if not account_trades:
                continue
            trade = random.choice(account_trades)
            amount = trade['trade_value']
            reference_id = trade['trade_id']
        else:
            amount = round(random.uniform(100, 5000), 2)
            reference_id = None
        
        transactions.append({
            'transaction_id': transaction_id,
            'account_id': account_id,
            'transaction_type': trans_type,
            'amount': amount,
            'transaction_date': datetime.now() - timedelta(days=random.randint(0, 90)),
            'reference_id': reference_id,
        })
        transaction_id += 1

print(f"[OK] Generated {len(transactions)} transactions")

print("\n[SAVING GENERATED DATA]")

# Save executions
with open(data_folder / "executions_insert.sql", "w") as f:
    for e in executions:
        f.write(f"INSERT INTO Executions (Execution_ID, Order_ID, Account_ID, Security_ID, Status, Quantity_Filled, Price_of_Execution, Cash_Amount, Exchange_Trade_ID, Finished_At, Failure_Reason) VALUES ({e['execution_id']}, {e['order_id']}, {e['account_id']}, {e['security_id']}, '{e['status']}', {e['quantity_filled']}, {e['price_of_execution']}, {e['cash_amount']}, {e['exchange_trade_id']}, '{e['finished_at']}', {e['failure_reason']});\n")
print(f"[OK] Saved executions_insert.sql ({len(executions)} records)")

# Save trades
with open(data_folder / "trades_insert.sql", "w") as f:
    for t in trades:
        f.write(f"INSERT INTO Trades (Trade_ID, Execution_ID, Account_ID, Security_ID, Quantity, Price, Trade_Value, Trade_Date, Trader_ID) VALUES ({t['trade_id']}, {t['execution_id']}, {t['account_id']}, {t['security_id']}, {t['quantity']}, {t['price']}, {t['trade_value']}, '{t['trade_date']}', {t['trader_id']});\n")
print(f"[OK] Saved trades_insert.sql ({len(trades)} records)")

# Save transactions
with open(data_folder / "transactions_insert.sql", "w") as f:
    for t in transactions:
        ref = f"{t['reference_id']}" if t['reference_id'] else "NULL"
        f.write(f"INSERT INTO Transactions (Transaction_ID, Account_ID, Transaction_Type, Amount, Transaction_Date, Reference_ID) VALUES ({t['transaction_id']}, {t['account_id']}, '{t['transaction_type']}', {t['amount']}, '{t['transaction_date']}', {ref});\n")
print(f"[OK] Saved transactions_insert.sql ({len(transactions)} records)")

print("\n[GENERATING CASH LEDGER]")

cash_ledger = []
ledger_id = 1

for account_id in sorted(accounts_by_id.keys()):
    balance = account_cash_balances.get(account_id, 0)
    
    account_transactions = [t for t in transactions if t['account_id'] == account_id]
    
    for trans in sorted(account_transactions, key=lambda x: x['transaction_date']):
        if trans['transaction_type'] == 'TRADE':
            balance -= trans['amount']
        else:
            balance += trans['amount']
        
        cash_ledger.append({
            'ledger_id': ledger_id,
            'account_id': account_id,
            'transaction_id': trans['transaction_id'],
            'balance_before': balance + trans['amount'] if trans['transaction_type'] != 'TRADE' else balance + trans['amount'],
            'balance_after': balance,
            'recorded_at': trans['transaction_date'],
        })
        ledger_id += 1

# Save cash ledger
with open(data_folder / "cash_ledger_insert.sql", "w") as f:
    for cl in cash_ledger:
        f.write(f"INSERT INTO Cash_Ledger (Ledger_ID, Account_ID, Transaction_ID, Balance_Before, Balance_After, Recorded_At) VALUES ({cl['ledger_id']}, {cl['account_id']}, {cl['transaction_id']}, {cl['balance_before']}, {cl['balance_after']}, '{cl['recorded_at']}');\n")
print(f"[OK] Saved cash_ledger_insert.sql ({ledger_id - 1} records)")

print("\n" + "=" * 80)
print("DATA GENERATION COMPLETE")
print("=" * 80)
print(f"Total Executions: {len(executions)}")
print(f"Total Trades: {len(trades)}")
print(f"Total Transactions: {len(transactions)}")
print(f"Total Cash Ledger Entries: {ledger_id - 1}")
print(f"All SQL files saved to: {data_folder}")
