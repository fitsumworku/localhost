import random
import re
from datetime import datetime, timedelta

def parse_trades_file(filepath):
    """Parse trades_insert.sql to extract settled and disputed trades"""
    trades = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to extract trade data from INSERT statement
        # VALUES (trade_id, execution_id, trade_price, shares, 'trade_status', 'trade_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*(\d+),\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            trade_id, execution_id, trade_price_str, shares, trade_status, trade_date = match
            
            # Only include SETTLED or DISPUTED trades
            if trade_status in ['SETTLED', 'DISPUTED']:
                try:
                    trade_price = float(trade_price_str)
                except ValueError:
                    trade_price = 0.0
                
                trades.append({
                    'trade_id': int(trade_id),
                    'execution_id': int(execution_id),
                    'trade_price': trade_price,
                    'shares': int(shares),
                    'trade_status': trade_status,
                    'trade_date': trade_date,
                })
        
        print(f"Parsed {len(trades)} SETTLED/DISPUTED trades from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing trades file: {e}")
        return None
    
    return trades if trades else None

def parse_accounts_file(filepath):
    """Parse accounts_insert.sql to extract account IDs and creation dates"""
    accounts = {}  # account_id -> created_date
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to match: VALUES (account_id, user_id, 'YYYY-MM-DD', ...)
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*'([^']*)',"
        
        matches = re.findall(pattern, content)
        for match in matches:
            account_id = int(match[0])
            created_date_str = match[2]  # Format: 'YYYY-MM-DD HH:MM:SS'
            # Extract just the date part
            if ' ' in created_date_str:
                created_date = created_date_str.split(' ')[0]
            else:
                created_date = created_date_str
            accounts[account_id] = created_date
        
        print(f"Parsed {len(accounts)} accounts with creation dates from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing accounts file: {e}")
        return None
    
    return accounts if accounts else None

def get_transaction_status_for_deposit_withdrawal():
    """Generate transaction status for deposits/withdrawals: 90% COMPLETED, 2% PENDING, 5% FAILED, 3% CANCELLED"""
    rand = random.random()
    if rand < 0.90:
        return 'COMPLETED'
    elif rand < 0.92:
        return 'PENDING'
    elif rand < 0.97:
        return 'FAILED'
    else:
        return 'CANCELLED'

def get_transaction_status_for_trade(trade_status):
    """Generate transaction status based on trade status"""
    if trade_status == 'PENDING_SETTLEMENT':
        return 'PENDING'
    else:  # SETTLED or DISPUTED
        return 'COMPLETED'

def generate_transactions(trades, accounts):
    """Generate transaction records for trades and account deposits/withdrawals"""
    transactions = []
    transaction_id = 1
    
    # First, add all trade transactions
    # Map trades to accounts (distribute trades across accounts)
    account_trades = {acc_id: [] for acc_id in accounts.keys()}
    for trade in trades:
        random_account = random.choice(list(accounts.keys()))
        account_trades[random_account].append(trade)
    
    # Create transactions for each trade
    for trade in trades:
        # Find which account has this trade
        account_id = None
        for acc_id, acc_trades in account_trades.items():
            if trade in acc_trades:
                account_id = acc_id
                break
        
        if account_id is None:
            # Assign to random account if not found
            account_id = random.choice(list(accounts.keys()))
        
        transaction_amount = trade['trade_price'] * trade['shares']
        transaction_status = get_transaction_status_for_trade(trade['trade_status'])
        
        transaction = {
            'transaction_id': transaction_id,
            'account_id': account_id,
            'trade_id': trade['trade_id'],
            'transaction_type': 'TRADE_SETTLEMENT',
            'transaction_amount': transaction_amount,
            'transaction_status': transaction_status,
            'transaction_date': trade['trade_date'],
        }
        
        transactions.append(transaction)
        transaction_id += 1
    
    # Now generate deposits and withdrawals for each account
    # Ensure initial deposit comes shortly after account creation
    for account_id in sorted(accounts.keys()):
        account_created = datetime.strptime(accounts[account_id], '%Y-%m-%d')
        
        # Initial deposit: 1-7 days after account creation
        initial_deposit_date = account_created + timedelta(days=random.randint(1, 7))
        deposit_amount = round(random.uniform(1000, 50000), 2)
        
        deposit_transaction = {
            'transaction_id': transaction_id,
            'account_id': account_id,
            'trade_id': None,
            'transaction_type': 'DEPOSIT',
            'transaction_amount': deposit_amount,
            'transaction_status': 'COMPLETED',  # Initial deposits always complete
            'transaction_date': initial_deposit_date.strftime('%Y-%m-%d'),
        }
        transactions.append(deposit_transaction)
        transaction_id += 1
        
        # Generate additional transactions (2-4 deposits and 2-4 withdrawals)
        # These should be after initial deposit but can be interspersed with trades
        num_additional_deposits = random.randint(2, 4)
        num_withdrawals = random.randint(2, 4)
        
        # Start from initial deposit date, then add random intervals
        current_date = initial_deposit_date + timedelta(days=random.randint(1, 5))
        
        # Generate additional deposits
        for _ in range(num_additional_deposits):
            deposit_amount = round(random.uniform(500, 20000), 2)
            deposit_date = current_date
            deposit_status = get_transaction_status_for_deposit_withdrawal()
            
            deposit_transaction = {
                'transaction_id': transaction_id,
                'account_id': account_id,
                'trade_id': None,
                'transaction_type': 'DEPOSIT',
                'transaction_amount': deposit_amount,
                'transaction_status': deposit_status,
                'transaction_date': deposit_date.strftime('%Y-%m-%d'),
            }
            transactions.append(deposit_transaction)
            transaction_id += 1
            current_date += timedelta(days=random.randint(3, 15))
        
        # Generate withdrawals
        for _ in range(num_withdrawals):
            withdrawal_amount = round(random.uniform(100, 10000), 2)
            withdrawal_date = current_date
            withdrawal_status = get_transaction_status_for_deposit_withdrawal()
            
            withdrawal_transaction = {
                'transaction_id': transaction_id,
                'account_id': account_id,
                'trade_id': None,
                'transaction_type': 'WITHDRAWAL',
                'transaction_amount': withdrawal_amount,
                'transaction_status': withdrawal_status,
                'transaction_date': withdrawal_date.strftime('%Y-%m-%d'),
            }
            transactions.append(withdrawal_transaction)
            transaction_id += 1
            current_date += timedelta(days=random.randint(3, 15))
    
    return transactions

def format_transactions_sql(transactions):
    """Format transactions as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\transactions_insert.sql",
        "-- Transactions generated by transactions_generator.py",
        "-- Execute this file in PostgreSQL to populate the Transactions table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(transactions)}",
        "-- Table: Transactions (Transaction_ID, Account_ID, Trade_ID, Transaction_Type, Transaction_Amount, Transaction_Status, Transaction_Date)",
        ""
    ]
    
    for transaction in transactions:
        trade_id_str = str(transaction['trade_id']) if transaction['trade_id'] else 'NULL'
        sql = f"INSERT INTO Transactions (Transaction_ID, Account_ID, Trade_ID, Transaction_Type, Transaction_Amount, Transaction_Status, Transaction_Date) VALUES ({transaction['transaction_id']}, {transaction['account_id']}, {trade_id_str}, '{transaction['transaction_type']}', {transaction['transaction_amount']}, '{transaction['transaction_status']}', '{transaction['transaction_date']}');"
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse input files
    trades_file = "trades_insert.sql"
    accounts_file = "accounts_insert.sql"
    
    print("Parsing trades file...")
    trades = parse_trades_file(trades_file)
    
    if trades is None:
        print("Error: Failed to parse trades file")
        exit(1)
    
    print("Parsing accounts file...")
    accounts = parse_accounts_file(accounts_file)
    
    if accounts is None:
        print("Error: Failed to parse accounts file")
        exit(1)
    
    print(f"\nStarting transaction generation...")
    print(f"SETTLED/DISPUTED Trades: {len(trades)}")
    print(f"Accounts: {len(accounts)}")
    
    # Generate transactions
    transactions = generate_transactions(trades, accounts)
    
    # Format as SQL
    sql_output = format_transactions_sql(transactions)
    
    # Write to file
    output_path = "transactions_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    # Calculate and display statistics
    trade_settlements = sum(1 for t in transactions if t['transaction_type'] == 'TRADE_SETTLEMENT')
    deposits = sum(1 for t in transactions if t['transaction_type'] == 'DEPOSIT')
    withdrawals = sum(1 for t in transactions if t['transaction_type'] == 'WITHDRAWAL')
    
    pending = sum(1 for t in transactions if t['transaction_status'] == 'PENDING')
    completed = sum(1 for t in transactions if t['transaction_status'] == 'COMPLETED')
    failed = sum(1 for t in transactions if t['transaction_status'] == 'FAILED')
    cancelled = sum(1 for t in transactions if t['transaction_status'] == 'CANCELLED')
    
    print(f"\nGenerated {len(transactions)} transactions")
    print(f"\nTransaction Type Distribution:")
    print(f"  TRADE_SETTLEMENT: {trade_settlements}")
    print(f"  DEPOSIT: {deposits} ({deposits/len(accounts):.1f} per account)")
    print(f"  WITHDRAWAL: {withdrawals} ({withdrawals/len(accounts):.1f} per account)")
    print(f"\nTransaction Status Distribution:")
    print(f"  COMPLETED: {completed} ({completed/len(transactions)*100:.1f}%)")
    print(f"  PENDING: {pending} ({pending/len(transactions)*100:.1f}%)")
    print(f"  FAILED: {failed} ({failed/len(transactions)*100:.1f}%)")
    print(f"  CANCELLED: {cancelled} ({cancelled/len(transactions)*100:.1f}%)")
    print(f"\nOutput written to {output_path}")
