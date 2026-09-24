import random
import re
from datetime import datetime, timedelta

def parse_securities_file(filepath):
    """Parse securities_insert.sql to extract active security IDs"""
    active_securities = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to match: VALUES (security_id, 'ticker', 'name', 'asset_type', 'exchange', 'ACTIVE', 'sector')
        # The Status field is in the 6th parameter position
        pattern = r"VALUES\s*\((\d+),\s*'[^']*',\s*'[^']*',\s*'[^']*',\s*'[^']*',\s*'ACTIVE',\s*'[^']*'\s*\)"
        
        matches = re.findall(pattern, content)
        active_securities = [int(m) for m in matches]
        
        print(f"Parsed {len(active_securities)} active securities from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found. Using hardcoded list.")
        return None
    except Exception as e:
        print(f"Error parsing securities file: {e}")
        return None
    
    return active_securities if active_securities else None

def parse_accounts_file(filepath):
    """Parse accounts_insert.sql to extract all account IDs"""
    account_ids = set()
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to match: VALUES (account_id, user_id, ...)
        pattern = r"VALUES\s*\((\d+),\s*(\d+),"
        
        matches = re.findall(pattern, content)
        account_ids = sorted(set(int(m[0]) for m in matches))
        
        print(f"Parsed {len(account_ids)} unique account IDs from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found. Using default range.")
        return None
    except Exception as e:
        print(f"Error parsing accounts file: {e}")
        return None
    
    return account_ids if account_ids else None

# Order types and statuses
order_types = ['MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT']
order_sides = ['B', 'S']

# Status distribution: 5% NEW, 2% WORKING, 17% PARTIALLY_FILLED, 66% FILLED, 10% CANCELLED
status_distribution = ['NEW'] * 5 + ['WORKING'] * 2 + ['PARTIALLY_FILLED'] * 17 + ['FILLED'] * 66 + ['CANCELLED'] * 10

def generate_orders(active_securities, account_ids, num_orders_per_account=15):
    """Generate orders ensuring sells have corresponding prior buys"""
    orders = []
    order_id = 1
    
    for account_id in account_ids:
        # Step 1: Generate orders with dates and attributes
        num_orders = random.randint(10, 20)
        order_templates = []
        
        for _ in range(num_orders):
            security_id = random.choice(active_securities)
            order_type = random.choice(order_types)
            quantity = random.randint(10, 1000)
            
            # Pre-assign as buy or sell (70/30 ratio)
            intended_side = 'S' if random.random() < 0.30 else 'B'
            
            # Generate dates (orders from past 90 days)
            days_ago = random.randint(0, 90)
            base_date = datetime.now() - timedelta(days=days_ago)
            created_date = base_date
            updated_date = created_date + timedelta(days=random.randint(0, 30))
            
            # Generate prices
            base_price = random.uniform(10, 500)
            
            # Select status based on distribution
            status = random.choice(status_distribution)
            
            order_templates.append({
                'account_id': account_id,
                'security_id': security_id,
                'order_type': order_type,
                'quantity': quantity,
                'base_price': base_price,
                'status': status,
                'created_date': created_date,
                'updated_date': updated_date,
                'intended_side': intended_side,
                'is_synthetic': False,  # Track if we added this for buy requirement
            })
        
        # Step 2: Sort by created date
        order_templates.sort(key=lambda x: x['created_date'])
        
        # Step 3: Add synthetic buy orders where needed for sells
        # Track what securities have been bought chronologically
        securities_bought_by_date = {}  # {security_id: {date: qty}}
        
        orders_to_add = []  # For synthetic buys
        
        for template in order_templates:
            sec_id = template['security_id']
            
            # Check if this is a sell without a prior buy
            if template['intended_side'] == 'S':
                has_prior_buy = False
                for prev_template in order_templates:
                    if (prev_template['security_id'] == sec_id and 
                        prev_template['created_date'] < template['created_date'] and
                        prev_template['intended_side'] == 'B'):
                        has_prior_buy = True
                        break
                
                # If no prior buy, add a synthetic one
                if not has_prior_buy:
                    # Create synthetic buy order before this sell
                    buy_days_before = random.randint(1, 30)
                    synthetic_date = template['created_date'] - timedelta(days=buy_days_before)
                    
                    orders_to_add.append({
                        'account_id': account_id,
                        'security_id': sec_id,
                        'order_type': random.choice(order_types),
                        'quantity': template['quantity'],
                        'base_price': random.uniform(10, 500),
                        'status': random.choice(status_distribution),
                        'created_date': synthetic_date,
                        'updated_date': synthetic_date + timedelta(days=random.randint(0, 30)),
                        'intended_side': 'B',
                        'is_synthetic': True,
                    })
        
        # Add synthetic orders to templates and resort
        order_templates.extend(orders_to_add)
        order_templates.sort(key=lambda x: x['created_date'])
        
        # Step 4: Resolve final sides and create order objects
        security_balances = {}
        
        for template in order_templates:
            sec_id = template['security_id']
            if sec_id not in security_balances:
                security_balances[sec_id] = 0
            
            # Try to honor intended side
            if template['intended_side'] == 'S' and security_balances[sec_id] > 0:
                side = 'S'
                template['quantity'] = min(template['quantity'], security_balances[sec_id])
                security_balances[sec_id] -= template['quantity']
            else:
                side = 'B'
                security_balances[sec_id] += template['quantity']
            
            limit_price = None
            if template['order_type'] in ['LIMIT', 'STOP_LIMIT']:
                if side == 'B':
                    limit_price = round(template['base_price'] * random.uniform(0.95, 0.99), 2)
                else:
                    limit_price = round(template['base_price'] * random.uniform(1.01, 1.05), 2)
            
            order = {
                'order_id': order_id,
                'account_id': template['account_id'],
                'security_id': template['security_id'],
                'side': side,
                'order_type': template['order_type'],
                'quantity': template['quantity'],
                'limit_price': limit_price,
                'status': template['status'],
                'created_date': template['created_date'].strftime('%Y-%m-%d %H:%M:%S'),
                'updated_date': template['updated_date'].strftime('%Y-%m-%d %H:%M:%S')
            }
            
            orders.append(order)
            order_id += 1
    
    return orders

def format_orders_sql(orders):
    """Format orders as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\orders_insert.sql",
        "-- Orders generated by generate_orders.py",
        "-- Execute this file in PostgreSQL to populate the Orders table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(orders)}",
        "-- Table: Orders (Order_ID, Account_ID, Security_ID, Side, Order_Type, Quantity_Ordered, Limit_Price, Order_Status, Created_Date, Updated_Date)",
        ""
    ]
    
    for order in orders:
        limit_price_str = f"{order['limit_price']}" if order['limit_price'] else "NULL"
        sql = f"INSERT INTO Orders (Order_ID, Account_ID, Security_ID, Side, Order_Type, Quantity_Ordered, Limit_Price, Order_Status, Created_Date, Updated_Date) VALUES ({order['order_id']}, {order['account_id']}, {order['security_id']}, '{order['side']}', '{order['order_type']}', {order['quantity']}, {limit_price_str}, '{order['status']}', '{order['created_date']}', '{order['updated_date']}');"
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse files to get dynamic data
    securities_file = "securities_insert.sql"
    accounts_file = "accounts_insert.sql"
    
    # Parse securities file
    active_securities = parse_securities_file(securities_file)
    
    # Parse accounts file
    account_ids = parse_accounts_file(accounts_file)
    
    print(f"\nStarting order generation...")
    print(f"Active securities: {len(active_securities)}")
    print(f"Account IDs: {len(account_ids)}")
    
    # Generate orders
    orders = generate_orders(active_securities, account_ids)
    
    # Format as SQL
    sql_output = format_orders_sql(orders)
    
    # Write to file
    output_path = "orders_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    print(f"\nGenerated {len(orders)} orders")
    print(f"Output written to {output_path}")