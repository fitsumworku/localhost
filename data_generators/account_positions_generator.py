import re
from collections import defaultdict
from datetime import datetime

def parse_orders_file(filename):
    """Parse orders to get order_id -> (side, security_id) mapping"""
    orders = {}
    try:
        with open(filename, 'r') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {filename}: {e}")
        return None
    
    # Pattern: VALUES (order_id, account_id, security_id, side ('B' or 'S'), ...)
    pattern = r"VALUES \((\d+),\s*\d+,\s*(\d+),\s*'([BS])',"
    
    matches = re.findall(pattern, content)
    for match in matches:
        order_id = int(match[0])
        security_id = int(match[1])
        side = match[2]
        orders[order_id] = {'security_id': security_id, 'side': side}
    
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
    
    # Pattern: VALUES (execution_id, order_id, ...)
    pattern = r"VALUES \((\d+),\s*(\d+),"
    
    matches = re.findall(pattern, content)
    for match in matches:
        execution_id = int(match[0])
        order_id = int(match[1])
        executions[execution_id] = order_id
    
    return executions if executions else None

def parse_trades_file(filename):
    """Parse trades to get trade_id -> (execution_id, shares, price, date) mapping"""
    trades = {}
    try:
        with open(filename, 'r') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {filename}: {e}")
        return None
    
    # Pattern: VALUES (trade_id, execution_id, price, shares, status, date)
    pattern = r"VALUES \((\d+),\s*(\d+),\s*([\d.]+),\s*(\d+),\s*'[^']*',\s*'([^']*)'\)"
    
    matches = re.findall(pattern, content)
    for match in matches:
        trade_id = int(match[0])
        execution_id = int(match[1])
        price = float(match[2])
        shares = int(match[3])
        date = match[4]
        trades[trade_id] = {
            'execution_id': execution_id,
            'shares': shares,
            'price': price,
            'date': date
        }
    
    return trades if trades else None

def parse_transactions_file(filename):
    """Parse transactions to get account_id -> list of trade_id for TRADE_SETTLEMENT"""
    transactions = defaultdict(list)
    try:
        with open(filename, 'r') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading {filename}: {e}")
        return None
    
    # Pattern: VALUES (transaction_id, account_id, trade_id, 'TRADE_SETTLEMENT', ...)
    pattern = r"VALUES \((\d+),\s*(\d+),\s*(\d+),\s*'TRADE_SETTLEMENT',"
    
    matches = re.findall(pattern, content)
    for match in matches:
        account_id = int(match[1])
        trade_id = int(match[2])
        transactions[account_id].append(trade_id)
    
    return transactions if dict(transactions) else None

def get_trade_info(trade_id, trades, executions, orders):
    """Get security_id and side by following the chain: trade -> execution -> order"""
    if trade_id not in trades:
        return None, None
    
    execution_id = trades[trade_id]['execution_id']
    if execution_id not in executions:
        return None, None
    
    order_id = executions[execution_id]
    if order_id not in orders:
        return None, None
    
    order_info = orders[order_id]
    return order_info['security_id'], order_info['side']

def generate_positions(transactions, trades, executions, orders):
    """Generate position records from transactions"""
    positions = {}  # (account_id, security_id) -> position data
    
    for account_id, trade_ids in transactions.items():
        for trade_id in trade_ids:
            security_id, side = get_trade_info(trade_id, trades, executions, orders)
            if security_id is None or side is None or trade_id not in trades:
                continue
            
            trade = trades[trade_id]
            shares = trade['shares']
            price = trade['price']
            date = trade['date']
            
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
    print(f"Parsed {len(orders)} orders with side information")
    
    print("Parsing executions file...")
    executions = parse_executions_file('executions_insert.sql')
    if not executions:
        print("Failed to parse executions")
        return
    print(f"Parsed {len(executions)} executions with order mappings")
    
    print("Parsing trades file...")
    trades = parse_trades_file('trades_insert.sql')
    if not trades:
        print("Failed to parse trades")
        return
    print(f"Parsed {len(trades)} trades")
    
    print("Parsing transactions file...")
    transactions = parse_transactions_file('transactions_insert.sql')
    if not transactions:
        print("Failed to parse transactions")
        return
    print(f"Parsed transactions for {len(transactions)} accounts")
    
    print("\nStarting position generation...")
    positions = generate_positions(transactions, trades, executions, orders)
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
