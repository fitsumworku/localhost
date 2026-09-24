import random
import re
from datetime import datetime, timedelta

def parse_executions_file(filepath):
    """Parse executions_insert.sql to extract filled executions"""
    executions = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to extract execution data from INSERT statement (updated format)
        # VALUES (execution_id, order_id, quantity_filled, price_of_execution, 'date_of_execution', 'settlement_date', 'status_of_execution', 'exchange_trade_id')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*([^,]*),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            execution_id, order_id, quantity_filled_str, price_str, date_of_execution, settlement_date, status_of_execution, exchange_trade_id = match
            
            # Only include FILLED or PARTIALLY_FILLED executions
            if status_of_execution in ['FILLED', 'PARTIALLY_FILLED']:
                try:
                    quantity_filled = float(quantity_filled_str)
                    price = float(price_str)
                except ValueError:
                    continue
                
                executions.append({
                    'execution_id': int(execution_id),
                    'order_id': int(order_id),
                    'quantity_filled': quantity_filled,
                    'price_of_execution': price,
                    'date_of_execution': date_of_execution,
                    'settlement_date': settlement_date,
                    'status_of_execution': status_of_execution,
                    'exchange_trade_id': exchange_trade_id,
                })
        
        print(f"Parsed {len(executions)} FILLED/PARTIALLY_FILLED executions from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing executions file: {e}")
        return None
    
    return executions if executions else None

def parse_orders_file(filepath):
    """Parse orders_insert.sql to extract security_id for each order"""
    order_securities = {}  # {order_id: security_id}
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to extract order data from INSERT statement (new format)
        # VALUES (order_id, account_id, security_id, 'side', quantity, 'status', 'created_date', 'updated_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*'([^']*)',\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            order_id, account_id, security_id, side, quantity, status, created_date, updated_date = match
            order_securities[int(order_id)] = int(security_id)
        
        print(f"Parsed {len(order_securities)} orders for security mapping")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing orders file: {e}")
        return None
    
    return order_securities if order_securities else None

def generate_trade_status():
    """Generate trade status based on distribution: 40% PENDING, 50% SETTLED, 8% DISPUTED, 2% REVERSED"""
    rand = random.random()
    if rand < 0.40:
        return 'PENDING'
    elif rand < 0.90:  # 0.40 + 0.50
        return 'SETTLED'
    elif rand < 0.98:  # 0.40 + 0.50 + 0.08
        return 'DISPUTED'
    else:
        return 'REVERSED'

def generate_trades(executions, order_securities):
    """Generate trade records from filled executions"""
    trades = []
    trade_id = 1
    
    for execution in executions:
        # Get security_id from order
        order_id = execution['order_id']
        security_id = order_securities.get(order_id)
        
        if security_id is None:
            print(f"Warning: Could not find security for order {order_id}")
            continue
        
        # Generate trade status with specified distribution
        trade_status = generate_trade_status()
        
        # Use the settlement date as the trade date
        # Parse settlement date and add 0-2 days
        settlement_date = datetime.strptime(execution['settlement_date'], '%Y-%m-%d %H:%M:%S')
        trade_date = settlement_date + timedelta(days=random.randint(0, 2))
        
        trade = {
            'trade_id': trade_id,
            'execution_id': execution['execution_id'],
            'security_id': security_id,
            'trade_price': execution['price_of_execution'],
            'shares': execution['quantity_filled'],
            'trade_status': trade_status,
            'trade_date': trade_date.strftime('%Y-%m-%d'),
        }
        
        trades.append(trade)
        trade_id += 1
    
    return trades

def format_trades_sql(trades):
    """Format trades as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\trades_insert.sql",
        "-- Trades generated by trades_generator.py",
        "-- Execute this file in PostgreSQL to populate the Trades table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(trades)}",
        "-- Table: Trades (Trade_ID, Execution_ID, Security_ID, Trade_Price, Shares, Trade_Status, Trade_Date)",
        "-- Status values: PENDING, SETTLED, DISPUTED, REVERSED",
        ""
    ]
    
    for trade in trades:
        sql = f"INSERT INTO Trades (Trade_ID, Execution_ID, Security_ID, Trade_Price, Shares, Trade_Status, Trade_Date) VALUES ({trade['trade_id']}, {trade['execution_id']}, {trade['security_id']}, {trade['trade_price']}, {trade['shares']}, '{trade['trade_status']}', '{trade['trade_date']}');"
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse input files
    executions_file = "executions_insert.sql"
    orders_file = "orders_insert.sql"
    
    print("Parsing executions file...")
    executions = parse_executions_file(executions_file)
    
    if executions is None:
        print("Error: Failed to parse executions file")
        exit(1)
    
    print("Parsing orders file...")
    order_securities = parse_orders_file(orders_file)
    
    if order_securities is None:
        print("Error: Failed to parse orders file")
        exit(1)
    
    print(f"\nStarting trade generation...")
    print(f"FILLED/PARTIALLY_FILLED Executions: {len(executions)}")
    
    # Generate trades
    trades = generate_trades(executions, order_securities)
    
    # Format as SQL
    sql_output = format_trades_sql(trades)
    
    # Write to file
    output_path = "trades_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    # Calculate and display status distribution
    pending = sum(1 for t in trades if t['trade_status'] == 'PENDING')
    settled = sum(1 for t in trades if t['trade_status'] == 'SETTLED')
    disputed = sum(1 for t in trades if t['trade_status'] == 'DISPUTED')
    reversed_trades = sum(1 for t in trades if t['trade_status'] == 'REVERSED')
    
    if len(trades) > 0:
        print(f"\nGenerated {len(trades)} trades")
        print(f"Trade Status Distribution:")
        print(f"  PENDING: {pending} ({pending/len(trades)*100:.1f}%)")
        print(f"  SETTLED: {settled} ({settled/len(trades)*100:.1f}%)")
        print(f"  DISPUTED: {disputed} ({disputed/len(trades)*100:.1f}%)")
        print(f"  REVERSED: {reversed_trades} ({reversed_trades/len(trades)*100:.1f}%)")
    print(f"Output written to {output_path}")
