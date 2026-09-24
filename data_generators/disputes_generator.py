import random
import re
from datetime import datetime, timedelta

# Dispute types and their descriptions
DISPUTE_TYPES = {
    'UNAUTHORIZED_TRANSACTION': [
        'Customer claims they did not authorize this transaction',
        'Account holder reports unauthorized activity',
        'Customer disputes the legitimacy of the trade execution',
    ],
    'DUPLICATE_CHARGE': [
        'Customer reports being charged twice for the same trade',
        'Duplicate transaction detected by customer',
        'Multiple charges for a single order',
    ],
    'INCORRECT_AMOUNT': [
        'Trade amount does not match order details',
        'Execution price differs from agreed terms',
        'Settlement amount calculated incorrectly',
    ],
    'INCORRECT_EXECUTION': [
        'Trade was not executed at the agreed price',
        'Execution quantity does not match order',
        'Wrong security was traded instead of requested',
    ],
    'SYSTEM_ERROR': [
        'System error caused incorrect trade execution',
        'Technical glitch during trade settlement',
        'Data corruption in trade record',
    ],
    'BILLING_ERROR': [
        'Fees or commissions charged incorrectly',
        'Tax calculation error on trade',
        'Incorrect clearing fees applied',
    ],
}

def parse_trades_file(filepath):
    """Parse trades_insert.sql to extract disputed trades"""
    trades = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (trade_id, execution_id, trade_price, shares, 'trade_status', 'trade_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*(\d+),\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            trade_id, execution_id, trade_price_str, shares, trade_status, trade_date = match
            
            # Only get DISPUTED trades
            if trade_status == 'DISPUTED':
                trades.append({
                    'trade_id': int(trade_id),
                    'trade_date': trade_date,
                })
        
        print(f"Parsed {len(trades)} DISPUTED trades from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing trades file: {e}")
        return None
    
    return trades if trades else None

def parse_transactions_file(filepath, trade_ids):
    """Parse transactions_insert.sql to find disputed transactions"""
    transactions = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (transaction_id, account_id, trade_id, 'transaction_type', amount, 'status', 'date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*'([^']*)',\s*([^,]*),\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            transaction_id, account_id, trade_id_str, transaction_type, amount, status, trans_date = match
            
            # Only get TRADE_SETTLEMENT transactions that are for disputed trades
            if transaction_type == 'TRADE_SETTLEMENT' and trade_id_str != 'NULL':
                trade_id = int(trade_id_str)
                if trade_id in trade_ids:
                    transactions.append({
                        'transaction_id': int(transaction_id),
                        'account_id': int(account_id),
                        'trade_id': trade_id,
                        'transaction_date': trans_date,
                    })
        
        print(f"Parsed {len(transactions)} disputed transactions from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing transactions file: {e}")
        return None
    
    return transactions if transactions else None

def parse_admin_users(user_roles_filepath, users_filepath):
    """Parse user_roles and users files to extract admin users"""
    admin_user_ids = set()
    user_details = {}
    
    try:
        # Parse user_roles to find admins (role_id = 2)
        with open(user_roles_filepath, 'r') as f:
            content = f.read()
        
        pattern = r"VALUES\s*\((\d+),\s*(\d+)\)"
        matches = re.findall(pattern, content)
        
        for user_id, role_id in matches:
            if role_id == '2':  # ADMIN role
                admin_user_ids.add(int(user_id))
        
        # Parse users to get user details
        with open(users_filepath, 'r') as f:
            content = f.read()
        
        pattern = r"VALUES\s*\((\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        matches = re.findall(pattern, content)
        
        for user_id, name, email, password, created_date, status, is_blacklisted in matches:
            user_id = int(user_id)
            if user_id in admin_user_ids:
                user_details[user_id] = {
                    'name': name,
                    'email': email,
                    'status': status,
                }
        
        print(f"Parsed {len(admin_user_ids)} admin users")
        
    except FileNotFoundError as e:
        print(f"Warning: {e}")
        return None
    except Exception as e:
        print(f"Error parsing user files: {e}")
        return None
    
    return list(admin_user_ids) if admin_user_ids else None

def get_dispute_status():
    """Generate dispute status: OPEN (10%), UNDER_REVIEW (25%), ESCALATED (15%), RESOLVED (50%)"""
    rand = random.random()
    if rand < 0.10:
        return 'OPEN'
    elif rand < 0.35:  # 0.10 + 0.25
        return 'UNDER_REVIEW'
    elif rand < 0.50:  # 0.35 + 0.15
        return 'ESCALATED'
    else:
        return 'RESOLVED'

def generate_disputes(transactions, admin_user_ids, trades_by_id):
    """Generate dispute records from disputed transactions"""
    disputes = []
    dispute_id = 1
    
    for transaction in transactions:
        # Select random admin user
        admin_id = random.choice(admin_user_ids)
        
        # Select dispute type
        dispute_type = random.choice(list(DISPUTE_TYPES.keys()))
        description = random.choice(DISPUTE_TYPES[dispute_type])
        
        # Generate status
        status = get_dispute_status()
        
        # Generate dates
        transaction_date = datetime.strptime(transaction['transaction_date'], '%Y-%m-%d')
        # Date created: 1-30 days after transaction
        date_created = transaction_date + timedelta(days=random.randint(1, 30))
        
        # Date resolved: only for resolved disputes, 7-60 days after creation
        date_resolved = None
        if status == 'RESOLVED':
            date_resolved = date_created + timedelta(days=random.randint(7, 60))
        
        dispute = {
            'dispute_id': dispute_id,
            'account_id': transaction['account_id'],
            'admin_id': admin_id,
            'transaction_id': transaction['transaction_id'],
            'dispute_type': dispute_type,
            'description': description,
            'status': status,
            'date_created': date_created.strftime('%Y-%m-%d'),
            'date_resolved': date_resolved.strftime('%Y-%m-%d') if date_resolved else None,
        }
        
        disputes.append(dispute)
        dispute_id += 1
    
    return disputes

def format_disputes_sql(disputes):
    """Format disputes as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\disputes_insert.sql",
        "-- Disputes generated by disputes_generator.py",
        "-- Execute this file in PostgreSQL to populate the Disputes table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(disputes)}",
        "-- Table: Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Dispute_Type, Description, Status, Date_Created, Date_Resolved)",
        ""
    ]
    
    for dispute in disputes:
        date_resolved_str = f"'{dispute['date_resolved']}'" if dispute['date_resolved'] else 'NULL'
        sql = f"INSERT INTO Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Dispute_Type, Description, Status, Date_Created, Date_Resolved) VALUES ({dispute['dispute_id']}, {dispute['account_id']}, {dispute['admin_id']}, {dispute['transaction_id']}, '{dispute['dispute_type']}', '{dispute['description']}', '{dispute['status']}', '{dispute['date_created']}', {date_resolved_str});"
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse input files
    trades_file = "trades_insert.sql"
    transactions_file = "transactions_insert.sql"
    user_roles_file = "user_roles_insert.sql"
    users_file = "users_insert.sql"
    
    print("Parsing trades file...")
    trades = parse_trades_file(trades_file)
    
    if trades is None or len(trades) == 0:
        print("Error: No disputed trades found")
        exit(1)
    
    # Create a set of disputed trade IDs for quick lookup
    disputed_trade_ids = {t['trade_id'] for t in trades}
    
    print("Parsing transactions file...")
    transactions = parse_transactions_file(transactions_file, disputed_trade_ids)
    
    if transactions is None or len(transactions) == 0:
        print("Error: No disputed transactions found")
        exit(1)
    
    print("Parsing admin users...")
    admin_user_ids = parse_admin_users(user_roles_file, users_file)
    
    if admin_user_ids is None or len(admin_user_ids) == 0:
        print("Error: No admin users found")
        exit(1)
    
    # Create dictionary of trades by ID for reference
    trades_by_id = {t['trade_id']: t for t in trades}
    
    print(f"\nStarting dispute generation...")
    print(f"Disputed Trades: {len(trades)}")
    print(f"Disputed Transactions: {len(transactions)}")
    print(f"Admin Users Available: {len(admin_user_ids)}")
    
    # Generate disputes
    disputes = generate_disputes(transactions, admin_user_ids, trades_by_id)
    
    # Format as SQL
    sql_output = format_disputes_sql(disputes)
    
    # Write to file
    output_path = "disputes_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    # Calculate and display statistics
    open_disputes = sum(1 for d in disputes if d['status'] == 'OPEN')
    under_review = sum(1 for d in disputes if d['status'] == 'UNDER_REVIEW')
    escalated = sum(1 for d in disputes if d['status'] == 'ESCALATED')
    resolved = sum(1 for d in disputes if d['status'] == 'RESOLVED')
    
    print(f"\nGenerated {len(disputes)} disputes")
    print(f"\nDispute Status Distribution:")
    print(f"  OPEN: {open_disputes} ({open_disputes/len(disputes)*100:.1f}%)")
    print(f"  UNDER_REVIEW: {under_review} ({under_review/len(disputes)*100:.1f}%)")
    print(f"  ESCALATED: {escalated} ({escalated/len(disputes)*100:.1f}%)")
    print(f"  RESOLVED: {resolved} ({resolved/len(disputes)*100:.1f}%)")
    
    dispute_types = {}
    for d in disputes:
        dispute_types[d['dispute_type']] = dispute_types.get(d['dispute_type'], 0) + 1
    
    print(f"\nDispute Type Distribution:")
    for dtype, count in sorted(dispute_types.items()):
        print(f"  {dtype}: {count}")
    
    print(f"\nOutput written to {output_path}")
