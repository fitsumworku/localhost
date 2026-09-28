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
    """Generate transaction status for deposits/withdrawals: 90% COMPLETED, 5% PENDING, 3% FAILED, 2% REVERSED"""
    rand = random.random()
    if rand < 0.90:
        return 'COMPLETED'
    elif rand < 0.95:
        return 'PENDING'
    elif rand < 0.98:
        return 'FAILED'
    else:
        return 'REVERSED'

def generate_transactions(accounts):
    """Generate transaction records for account deposits/withdrawals and other transactions"""
    transactions = []
    transaction_id = 1
    
    # Generate deposits and withdrawals for each account
    # Ensure initial deposit comes shortly after account creation
    for account_id in sorted(accounts.keys()):
        account_created = datetime.strptime(accounts[account_id], '%Y-%m-%d %H:%M:%S')
        
        # Initial deposit: 1-7 days after account creation
        initial_deposit_date = account_created + timedelta(days=random.randint(1, 7), hours=random.randint(0, 23), minutes=random.randint(0, 59), seconds=random.randint(0, 59))
        deposit_amount = round(random.uniform(1000, 50000), 2)
        
        deposit_transaction = {
            'transaction_id': transaction_id,
            'account_id': account_id,
            'transaction_type': 'DEPOSIT',
            'transaction_amount': deposit_amount,
            'transaction_status': 'COMPLETED',  # Initial deposits always complete
            'transaction_date': initial_deposit_date.strftime('%Y-%m-%d %H:%M:%S'),
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
            deposit_date = current_date + timedelta(hours=random.randint(0, 23), minutes=random.randint(0, 59), seconds=random.randint(0, 59))
            deposit_status = get_transaction_status_for_deposit_withdrawal()
            
            deposit_transaction = {
                'transaction_id': transaction_id,
                'account_id': account_id,
                'transaction_type': 'DEPOSIT',
                'transaction_amount': deposit_amount,
                'transaction_status': deposit_status,
                'transaction_date': deposit_date.strftime('%Y-%m-%d %H:%M:%S'),
            }
            transactions.append(deposit_transaction)
            transaction_id += 1
            current_date += timedelta(days=random.randint(3, 15))
        
        # Generate withdrawals
        for _ in range(num_withdrawals):
            withdrawal_amount = round(random.uniform(100, 10000), 2)
            withdrawal_date = current_date + timedelta(hours=random.randint(0, 23), minutes=random.randint(0, 59), seconds=random.randint(0, 59))
            withdrawal_status = get_transaction_status_for_deposit_withdrawal()
            
            withdrawal_transaction = {
                'transaction_id': transaction_id,
                'account_id': account_id,
                'transaction_type': 'WITHDRAWAL',
                'transaction_amount': withdrawal_amount,
                'transaction_status': withdrawal_status,
                'transaction_date': withdrawal_date.strftime('%Y-%m-%d %H:%M:%S'),
            }
            transactions.append(withdrawal_transaction)
            transaction_id += 1
            current_date += timedelta(days=random.randint(3, 15))
        
        # Generate occasional dividends, interest, and fees
        for _ in range(random.randint(0, 2)):
            # Dividends or interest
            dividend_amount = round(random.uniform(10, 500), 2)
            dividend_date = current_date + timedelta(hours=random.randint(0, 23), minutes=random.randint(0, 59), seconds=random.randint(0, 59))
            transaction_type = random.choice(['DIVIDEND', 'INTEREST'])
            
            dividend_transaction = {
                'transaction_id': transaction_id,
                'account_id': account_id,
                'transaction_type': transaction_type,
                'transaction_amount': dividend_amount,
                'transaction_status': 'COMPLETED',  # Dividends and interest are always completed
                'transaction_date': dividend_date.strftime('%Y-%m-%d %H:%M:%S'),
            }
            transactions.append(dividend_transaction)
            transaction_id += 1
            current_date += timedelta(days=random.randint(5, 30))
        
        # Generate occasional fees
        for _ in range(random.randint(0, 2)):
            fee_amount = round(random.uniform(5, 50), 2)
            fee_date = current_date + timedelta(hours=random.randint(0, 23), minutes=random.randint(0, 59), seconds=random.randint(0, 59))
            
            fee_transaction = {
                'transaction_id': transaction_id,
                'account_id': account_id,
                'transaction_type': 'FEE',
                'transaction_amount': fee_amount,
                'transaction_status': 'COMPLETED',  # Fees are always charged
                'transaction_date': fee_date.strftime('%Y-%m-%d %H:%M:%S'),
            }
            transactions.append(fee_transaction)
            transaction_id += 1
            current_date += timedelta(days=random.randint(5, 30))
    
    return transactions

def format_transactions_sql(transactions):
    """Format transactions as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\transactions_insert.sql",
        "-- Transactions generated by transactions_generator.py",
        "-- Execute this file in PostgreSQL to populate the Transactions table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(transactions)}",
        "-- Table: Transactions (Transaction_ID, Account_ID, Transaction_Amount, Transaction_Type, Transaction_Date, Transaction_Status)",
        "-- Transaction_Type options: DEPOSIT, WITHDRAWAL, DIVIDEND, INTEREST, FEE",
        "-- Transaction_Status options: PENDING, COMPLETED, FAILED, DISPUTED, REVERSED",
        ""
    ]
    
    for transaction in transactions:
        sql = f"INSERT INTO Transactions (Transaction_ID, Account_ID, Transaction_Amount, Transaction_Type, Transaction_Date, Transaction_Status) VALUES ({transaction['transaction_id']}, {transaction['account_id']}, {transaction['transaction_amount']}, '{transaction['transaction_type']}', '{transaction['transaction_date']}', '{transaction['transaction_status']}');"
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse input file
    accounts_file = "accounts_insert.sql"
    
    print("Parsing accounts file...")
    accounts = parse_accounts_file(accounts_file)
    
    if accounts is None:
        print("Error: Failed to parse accounts file")
        exit(1)
    
    print(f"\nStarting transaction generation...")
    print(f"Accounts: {len(accounts)}")
    
    # Generate transactions
    transactions = generate_transactions(accounts)
    
    # Format as SQL
    sql_output = format_transactions_sql(transactions)
    
    # Write to file
    output_path = "transactions_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    # Calculate and display transaction type distribution
    deposit_count = sum(1 for t in transactions if t['transaction_type'] == 'DEPOSIT')
    withdrawal_count = sum(1 for t in transactions if t['transaction_type'] == 'WITHDRAWAL')
    dividend_count = sum(1 for t in transactions if t['transaction_type'] == 'DIVIDEND')
    interest_count = sum(1 for t in transactions if t['transaction_type'] == 'INTEREST')
    fee_count = sum(1 for t in transactions if t['transaction_type'] == 'FEE')
    
    print(f"\nGenerated {len(transactions)} transactions")
    print(f"Transaction Type Distribution:")
    print(f"  DEPOSIT: {deposit_count} ({deposit_count/len(transactions)*100:.1f}%)")
    print(f"  WITHDRAWAL: {withdrawal_count} ({withdrawal_count/len(transactions)*100:.1f}%)")
    print(f"  DIVIDEND: {dividend_count} ({dividend_count/len(transactions)*100:.1f}%)")
    print(f"  INTEREST: {interest_count} ({interest_count/len(transactions)*100:.1f}%)")
    print(f"  FEE: {fee_count} ({fee_count/len(transactions)*100:.1f}%)")
    print(f"Output written to {output_path}")
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
