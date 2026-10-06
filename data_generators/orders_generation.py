#!/usr/bin/env python3
"""
orders_generation.py - Generate trading orders with full lifecycle tracking.

Orders represent client requests to buy/sell securities:
- BUY orders: Requested_Amount (cash to spend) is provided, Quantity_Ordered is NULL
- SELL orders: Quantity_Ordered (shares to sell) is provided, Requested_Amount is NULL
- Status lifecycle: SUBMITTED â†’ ACCEPTED â†’ IN_EXECUTION â†’ (FILLED|REJECTED|CANCELLED)
- Timestamps: Created_Date, Accepted_At (when status moves to ACCEPTED), Terminal_At (when status reaches terminal)
"""

import random
import re
import uuid
from pathlib import Path
from datetime import datetime, timedelta

def parse_securities_file(filepath):
    """Parse securities_insert.sql to extract active security tickers with assigned IDs"""
    active_securities = []
    security_counter = 1
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to match multi-line: INSERT ... VALUES ('TICKER', 'Name', 'ASSET_TYPE', 'EXCHANGE', 'QUOTE_CURRENCY', Base_Curr, 'STATUS', Sector)
        pattern = r"VALUES\s*\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*([^,]*),\s*'([^']*)',\s*([^)]*)\)"
        
        matches = re.findall(pattern, content)
        
        for match in matches:
            ticker = match[0]
            name = match[1]
            asset_type = match[2]
            exchange = match[3]
            quote_curr = match[4]
            base_curr = match[5].strip()
            status = match[6]
            
            if status == 'ACTIVE':
                active_securities.append((security_counter, ticker, asset_type, exchange))
            
            security_counter += 1
        
        print(f"Parsed {len(active_securities)} active securities from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing securities file: {e}")
        return None
    
    return active_securities if active_securities else None

def parse_accounts_file(filepath):
    """Parse accounts_insert.sql to extract all account IDs"""
    account_ids = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to match: VALUES (account_id, user_id, ...)
        pattern = r"VALUES\s*\((\d+),\s*(\d+),"
        
        matches = re.findall(pattern, content)
        account_ids = sorted(set(int(m[0]) for m in matches))
        
        print(f"Parsed {len(account_ids)} unique account IDs from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing accounts file: {e}")
        return None
    
    return account_ids if account_ids else None

def generate_orders(active_securities, account_ids):
    """Generate orders with proper status lifecycle"""
    orders = []
    order_id = 1
    
    # Status weights for order distribution
    # 40% SUBMITTED/ACCEPTED (still pending), 40% IN_EXECUTION, 20% terminal (FILLED/REJECTED/CANCELLED)
    status_choices = ['SUBMITTED', 'ACCEPTED', 'IN_EXECUTION', 'FILLED', 'REJECTED', 'CANCELLED']
    status_weights = [0.15, 0.25, 0.40, 0.10, 0.05, 0.05]
    
    for account_id in account_ids:
        # Generate 10-20 orders per account
        num_orders = random.randint(10, 20)
        
        for _ in range(num_orders):
            # Select a random security: tuple format (security_id, ticker, asset_type, exchange)
            security = random.choice(active_securities)
            security_id = security[0]  # Extract just the ID from the tuple
            
            # Determine side (70% BUY, 30% SELL)
            side = 'B' if random.random() < 0.70 else 'S'
            
            # Generate base dates (orders from past 90 days)
            days_ago = random.randint(0, 90)
            created_date = datetime.now() - timedelta(days=days_ago, hours=random.randint(0, 23), 
                                                       minutes=random.randint(0, 59), seconds=random.randint(0, 59))
            
            # Randomly select order status
            order_status = random.choices(status_choices, weights=status_weights)[0]
            
            # Generate order values based on side
            if side == 'B':
                # BUY: specify requested amount in USD
                requested_amount = round(random.uniform(1000, 50000), 2)
                quantity_ordered = None
            else:
                # SELL: specify quantity of shares (10-1000)
                quantity_ordered = round(random.uniform(10, 1000), 12)
                requested_amount = None
            
            # Determine timestamp flow based on status
            if order_status == 'SUBMITTED':
                accepted_at = None
                terminal_at = None
                rejection_reason = None
            elif order_status in ('ACCEPTED', 'IN_EXECUTION'):
                # Move to ACCEPTED at some point after creation
                accepted_at = created_date + timedelta(minutes=random.randint(1, 30))
                terminal_at = None
                rejection_reason = None
            else:  # FILLED, REJECTED, CANCELLED (terminal states)
                # Must have accepted_at before terminal
                accepted_at = created_date + timedelta(minutes=random.randint(1, 30))
                terminal_at = accepted_at + timedelta(hours=random.randint(0, 8), minutes=random.randint(0, 59))
                
                if order_status == 'REJECTED':
                    rejection_reason = random.choice([
                        'Insufficient funds',
                        'Invalid security',
                        'Quantity exceeds available balance',
                        'Market closed',
                        'Order size too large'
                    ])
                else:
                    rejection_reason = None
            
            # Generate unique Client_Request_ID (UUID)
            client_request_id = str(uuid.uuid4())
            
            orders.append({
                'order_id': order_id,
                'account_id': account_id,
                'security_id': security_id,
                'client_request_id': client_request_id,
                'side': side,
                'requested_amount': requested_amount,
                'quantity_ordered': quantity_ordered,
                'order_status': order_status,
                'created_date': created_date,
                'updated_date': terminal_at or accepted_at or created_date,
                'accepted_at': accepted_at,
                'terminal_at': terminal_at,
                'rejection_reason': rejection_reason
            })
            
            order_id += 1
    
    return orders

def format_orders_sql(orders):
    """Format orders as SQL INSERT statements"""
    sql_lines = [
        "-- Orders generated by orders_generation.py",
        "-- Execute this file in PostgreSQL to populate the Orders table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(orders)}",
        "-- Status distribution: submitted/accepted/in_execution/filled/rejected/cancelled",
        "-- BUY orders: Requested_Amount specified, Quantity_Ordered is NULL",
        "-- SELL orders: Quantity_Ordered specified, Requested_Amount is NULL\n"
    ]
    
    for order in orders:
        # Handle NULL values
        requested_amount_str = f"{order['requested_amount']}" if order['requested_amount'] is not None else "NULL"
        quantity_ordered_str = f"{order['quantity_ordered']}" if order['quantity_ordered'] is not None else "NULL"
        accepted_at_str = f"'{order['accepted_at'].strftime('%Y-%m-%d %H:%M:%S')}'" if order['accepted_at'] else "NULL"
        terminal_at_str = f"'{order['terminal_at'].strftime('%Y-%m-%d %H:%M:%S')}'" if order['terminal_at'] else "NULL"
        rejection_reason_str = f"'{order['rejection_reason']}'" if order['rejection_reason'] else "NULL"
        
        sql = (
            f"INSERT INTO Orders (Order_ID, Account_ID, Security_ID, Client_Request_ID, Side, "
            f"Requested_Amount, Quantity_Ordered, Order_Status, Created_Date, Updated_Date, "
            f"Accepted_At, Terminal_At, Rejection_Reason) "
            f"VALUES ({order['order_id']}, {order['account_id']}, {order['security_id']}, "
            f"'{order['client_request_id']}', '{order['side']}', {requested_amount_str}, {quantity_ordered_str}, "
            f"'{order['order_status']}', '{order['created_date'].strftime('%Y-%m-%d %H:%M:%S')}', "
            f"'{order['updated_date'].strftime('%Y-%m-%d %H:%M:%S')}', {accepted_at_str}, {terminal_at_str}, "
            f"{rejection_reason_str});"
        )
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse input files
    print("Parsing input files...")
    data_folder = Path(__file__).parent.parent / "data"
    securities_file = data_folder / "securities_insert.sql"
    accounts_file = data_folder / "accounts_insert.sql"
    
    active_securities = parse_securities_file(securities_file)
    if active_securities is None:
        print("Error: Failed to parse securities file")
        exit(1)
    
    account_ids = parse_accounts_file(accounts_file)
    if account_ids is None:
        print("Error: Failed to parse accounts file")
        exit(1)
    
    # Generate orders
    print("\nGenerating orders...")
    orders = generate_orders(active_securities, account_ids)
    
    # Format as SQL
    sql_content = format_orders_sql(orders)
    
    # Save to file
    data_folder = Path(__file__).parent.parent / "data"
    output_path = data_folder / "orders_insert.sql"
    with open(output_path, "w") as f:
        f.write(sql_content)
    
    # Print summary
    print(f"\nâœ“ Generated {len(orders)} orders")
    print(f"âœ“ Average orders per account: {len(orders) / len(account_ids):.1f}")
    
    buy_count = sum(1 for o in orders if o['side'] == 'B')
    sell_count = sum(1 for o in orders if o['side'] == 'S')
    print(f"  * BUY orders: {buy_count} ({buy_count/len(orders)*100:.1f}%)")
    print(f"  * SELL orders: {sell_count} ({sell_count/len(orders)*100:.1f}%)")
    
    status_counts = {}
    for o in orders:
        status = o['order_status']
        status_counts[status] = status_counts.get(status, 0) + 1
    
    print(f"  * Status distribution:")
    for status, count in sorted(status_counts.items()):
        print(f"    - {status}: {count} ({count/len(orders)*100:.1f}%)")
    
    print(f"\nâœ“ Saved to {output_path}")
    print(f"âœ“ Note: Run AFTER securities_generation.py and accounts_generation.py")

