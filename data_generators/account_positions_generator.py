import re
from collections import defaultdict
from datetime import datetime

def parse_orders_file(filename):
    """Parse orders to get order details"""
    orders = {}
    try:
        with open(filename, 'r') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {filename}: {e}")
        return None
    
    # Pattern: VALUES (order_id, account_id, security_id, 'side', quantity, 'status', 'created_date', 'updated_date')
    pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*'([BS])',\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
    
    matches = re.findall(pattern, content)
    for match in matches:
        order_id = int(match[0])
        account_id = int(match[1])
        security_id = int(match[2])
        side = match[3]
        quantity = int(match[4])
        status = match[5]
        created_date = match[6]
        updated_date = match[7]
        
        orders[order_id] = {
            'account_id': account_id,
            'security_id': security_id,
            'side': side,
            'quantity': quantity,
            'status': status,
            'created_date': created_date,
            'updated_date': updated_date,
        }
    
    return orders if orders else None

def parse_executions_file(filename):
    """Parse executions to get execution_id -> order_id mapping"""
    executions = {}
    try:
        with open(filename, 'r') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {filename}: {e}")
        return None
    
    # Pattern: VALUES (execution_id, order_id, quantity_filled, price, 'date_of_execution', 'settlement_date', 'status', 'exchange_id')
    pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*([^,]*),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
    
    matches = re.findall(pattern, content)
    for match in matches:
        execution_id = int(match[0])
        order_id = int(match[1])
        quantity_filled = float(match[2])
        price = float(match[3])
        
        executions[execution_id] = {
            'order_id': order_id,
            'quantity_filled': quantity_filled,
            'price': price,
        }
    
    return executions if executions else None

def parse_trades_file(filename):
    """Parse trades to get trade details"""
    trades = {}
    try:
        with open(filename, 'r') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {filename}: {e}")
        return None
    
    # Pattern: VALUES (trade_id, execution_id, security_id, trade_price, shares, 'status', 'trade_date')
    pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*([^,]*),\s*([^,]*),\s*'([^']*)',\s*'([^']*)'\s*\)"
    
    matches = re.findall(pattern, content)
    for match in matches:
        trade_id = int(match[0])
        execution_id = int(match[1])
        security_id = int(match[2])
        price = float(match[3])
        shares = float(match[4])
        status = match[5]
        date = match[6]
        
        trades[trade_id] = {
            'execution_id': execution_id,
            'security_id': security_id,
            'shares': shares,
            'price': price,
            'status': status,
            'date': date
        }
    
    return trades if trades else None

def generate_positions(orders, executions, trades):
    """Generate position records from orders and trades"""
    positions = {}  # (account_id, security_id) -> position data
    
    # Process each trade to build positions
    for trade_id, trade in trades.items():
        execution_id = trade['execution_id']
        security_id = trade['security_id']
        shares = trade['shares']
        price = trade['price']
        date = trade['date']
        
        # Get the order for this execution
        if execution_id not in executions:
            continue
        
        execution = executions[execution_id]
        order_id = execution['order_id']
        
        if order_id not in orders:
            continue
        
        order = orders[order_id]
        account_id = order['account_id']
        side = order['side']
        
        key = (account_id, security_id)
        if key not in positions:
            positions[key] = {
                'account_id': account_id,
                'security_id': security_id,
                'net_shares': 0,          # Total after buys and sells
                'total_shares_bought': 0, # Total from buy orders only (for average price)
                'buy_value': 0.0,         # Sum of (price * shares) for all buys
                'updated_date': date
            }
        
        if side == 'B':  # Buy
            positions[key]['net_shares'] += shares
            positions[key]['total_shares_bought'] += shares
            positions[key]['buy_value'] += price * shares
        else:  # Sell
            positions[key]['net_shares'] -= shares
        
        # Update date to most recent
        if date > positions[key]['updated_date']:
            positions[key]['updated_date'] = date
    
    return positions

def format_positions_sql(positions):
    """Format positions as SQL INSERT statements"""
    # Count active positions (net_shares > 0)
    active_count = len([p for p in positions.values() if p['net_shares'] > 0])
    
    lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\account_positions_insert.sql",
        "-- Account Positions generated by account_positions_generator.py",
        "-- Execute this file in PostgreSQL to populate the Account_Positions table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {active_count}",
        "-- Table: Account_Positions (Position_ID, Account_ID, Security_ID, Total_Shares, Average_Price, Updated_Date)",
        ""
    ]
    
    position_id = 1
    for (account_id, security_id), pos_data in sorted(positions.items()):
        if pos_data['net_shares'] <= 0:
            continue  # Skip positions with zero or negative shares
        
        # Weighted average price per share (total buy value / total buy shares)
        average_price = pos_data['buy_value'] / pos_data['total_shares_bought'] if pos_data['total_shares_bought'] > 0 else 0.0
        
        line = f"INSERT INTO Account_Positions (Position_ID, Account_ID, Security_ID, Total_Shares, Average_Price, Updated_Date) VALUES ({position_id}, {account_id}, {security_id}, {pos_data['net_shares']}, {average_price}, '{pos_data['updated_date']}');"
        lines.append(line)
        position_id += 1
    
    return "\n".join(lines)

def main():
    print("Parsing orders file...")
    orders = parse_orders_file('orders_insert.sql')
    if not orders:
        print("Failed to parse orders")
        return
    print(f"Parsed {len(orders)} orders")
    
    print("Parsing executions file...")
    executions = parse_executions_file('executions_insert.sql')
    if not executions:
        print("Failed to parse executions")
        return
    print(f"Parsed {len(executions)} executions")
    
    print("Parsing trades file...")
    trades = parse_trades_file('trades_insert.sql')
    if not trades:
        print("Failed to parse trades")
        return
    print(f"Parsed {len(trades)} trades")
    
    print("\nStarting position generation...")
    positions = generate_positions(orders, executions, trades)
    active_positions = [p for p in positions.values() if p['net_shares'] > 0]
    print(f"Generated {len(active_positions)} active positions")
    
    # Calculate some statistics
    total_shares = sum(p['net_shares'] for p in active_positions)
    total_value = sum(p['buy_value'] for p in active_positions if p['total_shares_bought'] > 0)
    
    print(f"\nPosition Statistics:")
    print(f"  Total Shares Held: {total_shares:,}")
    print(f"  Total Buy Value: ${total_value:,.2f}")
    
    sql_output = format_positions_sql(positions)
    
    with open('account_positions_insert.sql', 'w') as f:
        f.write(sql_output)
    
    print("\nOutput written to account_positions_insert.sql")

if __name__ == '__main__':
    main()
