import random
import re
from datetime import datetime, timedelta

def parse_securities_file(filepath):
    """Parse securities_insert.sql to extract security exchange mappings"""
    security_exchanges = {}  # {security_id: exchange}
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (security_id, 'ticker', 'name', 'asset_type', 'exchange', 'status', 'sector')
        pattern = r"VALUES\s*\((\d+),\s*'[^']*',\s*'[^']*',\s*'[^']*',\s*'([^']*)',\s*'[^']*',\s*'[^']*'\s*\)"
        
        matches = re.findall(pattern, content)
        for sec_id, exchange in matches:
            security_exchanges[int(sec_id)] = exchange
        
        print(f"Parsed {len(security_exchanges)} securities with exchanges")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing securities file: {e}")
        return None
    
    return security_exchanges if security_exchanges else None

def parse_orders_file(filepath):
    """Parse orders_insert.sql to extract order details"""
    orders = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern to extract order data from INSERT statement (new format)
        # VALUES (order_id, account_id, security_id, 'side', quantity, 'status', 'created_date', 'updated_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*'([^']*)',\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            order_id, account_id, security_id, side, quantity, status, created_date, updated_date = match
            
            orders.append({
                'order_id': int(order_id),
                'account_id': int(account_id),
                'security_id': int(security_id),
                'side': side,
                'quantity': int(quantity),
                'status': status,
                'created_date': created_date,
                'updated_date': updated_date,
            })
        
        print(f"Parsed {len(orders)} orders from {filepath}")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing orders file: {e}")
        return None
    
    return orders if orders else None

def generate_exchange_trade_id(exchange, counter):
    """Generate exchange trade ID in format: EXCHANGE-SequentialNumber
    Uses sequential counter per exchange to ensure uniqueness"""
    return f"{exchange}-{counter:08d}"

def determine_execution_status(order_status):
    """Determine execution status based on order status"""
    if order_status == 'IN_EXECUTION':
        return 'FILLED'
    elif order_status == 'PENDING':
        return 'PENDING'
    elif order_status == 'CANCELLED':
        return 'FAILED'
    else:
        return 'PENDING'  # Default

def generate_execution_price(base_price):
    """Generate execution price based on base price"""
    # Add some randomness to the base price (0.98 to 1.02 factor)
    return round(base_price * random.uniform(0.98, 1.02), 2)

def generate_executions(orders, security_exchanges):
    """Generate execution records for all orders"""
    executions = []
    execution_id = 1
    
    # Cache for prices per security to maintain consistency
    security_price_history = {}  # {security_id: list of prices}
    
    # Counter per exchange for unique trade IDs
    exchange_counters = {}  # {exchange: counter}
    
    for order in orders:
        sec_id = order['security_id']
        
        # Initialize price history for this security if not exists
        if sec_id not in security_price_history:
            security_price_history[sec_id] = []
        
        # Generate base price based on security's price history
        if security_price_history[sec_id]:
            # Use recent price as anchor, add small randomness
            recent_price = security_price_history[sec_id][-1]
            base_price = recent_price * random.uniform(0.98, 1.02)
        else:
            # First price for this security
            base_price = random.uniform(10, 500)
        
        # Generate quantity filled (mostly equal to quantity ordered)
        if random.random() < 0.85:  # 85% are fully filled
            quantity_filled = order['quantity']
        else:
            # Partial fill
            quantity_filled = random.randint(int(order['quantity'] * 0.5), order['quantity'])
        
        # Generate execution price
        execution_price = generate_execution_price(base_price)
        
        # Record this price in history
        security_price_history[sec_id].append(execution_price)
        
        # Generate execution time (shortly after created date, within a few minutes)
        created_dt = datetime.strptime(order['created_date'], '%Y-%m-%d %H:%M:%S')
        execution_time = created_dt + timedelta(minutes=random.randint(0, 30))
        
        # Generate settlement date (after execution, within 2-3 days for normal settlement)
        settlement_date = execution_time + timedelta(days=random.randint(1, 3))
        
        # Determine settlement status
        settlement_status = determine_execution_status(order['status'])
        
        # Get exchange for this security
        exchange = security_exchanges.get(sec_id, 'NYSE')
        
        # Initialize counter for this exchange if needed
        if exchange not in exchange_counters:
            exchange_counters[exchange] = 1
        
        # Generate unique exchange trade ID
        exchange_trade_id = generate_exchange_trade_id(exchange, exchange_counters[exchange])
        exchange_counters[exchange] += 1
        
        execution = {
            'execution_id': execution_id,
            'order_id': order['order_id'],
            'quantity_filled': quantity_filled,
            'price_of_execution': execution_price,
            'date_of_execution': execution_time.strftime('%Y-%m-%d %H:%M:%S'),
            'settlement_date': settlement_date.strftime('%Y-%m-%d %H:%M:%S'),
            'status_of_execution': settlement_status,
            'exchange_trade_id': exchange_trade_id,
        }
        
        executions.append(execution)
        execution_id += 1
    
    return executions

def format_executions_sql(executions):
    """Format executions as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\executions_insert.sql",
        "-- Executions generated by executions_generation.py",
        "-- Execute this file in PostgreSQL to populate the Executions table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(executions)}",
        "-- Table: Executions (Execution_ID, Order_ID, Quantity_Filled, Price_Of_Execution, Date_Of_Execution, Settlement_Date, Status_Of_Execution, Exchange_Trade_ID)",
        "-- Status values: PENDING, FILLED, PARTIALLY_FILLED, FAILED",
        ""
    ]
    
    for execution in executions:
        sql = f"INSERT INTO Executions (Execution_ID, Order_ID, Quantity_Filled, Price_Of_Execution, Date_Of_Execution, Settlement_Date, Status_Of_Execution, Exchange_Trade_ID) VALUES ({execution['execution_id']}, {execution['order_id']}, {execution['quantity_filled']}, {execution['price_of_execution']}, '{execution['date_of_execution']}', '{execution['settlement_date']}', '{execution['status_of_execution']}', '{execution['exchange_trade_id']}');"
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse input files
    securities_file = "securities_insert.sql"
    orders_file = "orders_insert.sql"
    
    print("Parsing securities file...")
    security_exchanges = parse_securities_file(securities_file)
    
    if security_exchanges is None:
        print("Error: Failed to parse securities file")
        exit(1)
    
    print("Parsing orders file...")
    orders = parse_orders_file(orders_file)
    
    if orders is None:
        print("Error: Failed to parse orders file")
        exit(1)
    
    print(f"\nStarting execution generation...")
    print(f"Securities: {len(security_exchanges)}")
    print(f"Orders: {len(orders)}")
    
    # Generate executions
    executions = generate_executions(orders, security_exchanges)
    
    # Format as SQL
    sql_output = format_executions_sql(executions)
    
    # Write to file
    output_path = "executions_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    print(f"\nGenerated {len(executions)} executions")
    print(f"Output written to {output_path}")
