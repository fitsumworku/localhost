import random
import re
from datetime import datetime, timedelta
from collections import defaultdict

def parse_orders_file(filepath):
    """Parse orders_insert.sql to create order_id -> side mapping"""
    order_sides = {}
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (order_id, account_id, security_id, 'side', quantity, 'status', 'created_date', 'updated_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*'([^']*)',\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            order_id, account_id, security_id, side, quantity, status, created_date, updated_date = match
            order_sides[int(order_id)] = side
        
        print(f"Parsed {len(order_sides)} orders with side information")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing orders file: {e}")
        return None
    
    return order_sides if order_sides else None

def parse_executions_file(filepath):
    """Parse executions_insert.sql to create execution_id -> order_id mapping"""
    execution_orders = {}
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (execution_id, order_id, quantity_filled, price, 'date_of_execution', 'settlement_date', 'status', 'exchange_id')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*([^,]*),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            execution_id, order_id, quantity_filled, price, date_of_execution, settlement_date, status, exchange_id = match
            execution_orders[int(execution_id)] = int(order_id)
        
        print(f"Parsed {len(execution_orders)} executions with order mappings")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing executions file: {e}")
        return None
    
    return execution_orders if execution_orders else None

def parse_trades_file(filepath):
    """Parse trades_insert.sql to extract trades for cash ledger"""
    trades = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (trade_id, execution_id, security_id, trade_price, shares, 'status', 'trade_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*([^,]*),\s*([^,]*),\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            trade_id, execution_id, security_id, trade_price, shares, status, trade_date = match
            try:
                price = float(trade_price)
                quantity = float(shares)
            except ValueError:
                continue
            
            trades.append({
                'trade_id': int(trade_id),
                'execution_id': int(execution_id),
                'trade_price': price,
                'shares': quantity,
                'status': status,
                'trade_date': trade_date,
            })
        
        print(f"Parsed {len(trades)} trades for cash ledger")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing trades file: {e}")
        return None
    
    return trades if trades else None

def parse_transactions_file(filepath):
    """Parse transactions_insert.sql to extract all transactions"""
    transactions = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (transaction_id, account_id, transaction_amount, 'transaction_type', 'transaction_date', 'transaction_status')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            transaction_id, account_id, amount_str, transaction_type, trans_date, status = match
            
            try:
                amount_float = float(amount_str)
            except ValueError:
                amount_float = 0.0
            
            transactions.append({
                'transaction_id': int(transaction_id),
                'account_id': int(account_id),
                'transaction_type': transaction_type,
                'amount': amount_float,
                'status': status,
                'date': trans_date,
            })
        
        print(f"Parsed {len(transactions)} transactions")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing transactions file: {e}")
        return None
    
    return transactions if transactions else None

def generate_ledger_entries(transactions, trades, order_sides, execution_orders):
    """Generate cash ledger entries from transactions and trades"""
    ledger_entries = []
    ledger_id = 1
    
    # Group all items by account
    account_items = defaultdict(list)
    
    # Add transactions to account items
    for trans in transactions:
        account_items[trans['account_id']].append({
            'type': 'transaction',
            'data': trans,
            'date': datetime.strptime(trans['date'], '%Y-%m-%d'),
        })
    
    # Add trades to account items (map to accounts randomly since trades don't have account_id)
    if len(transactions) > 0:
        sample_account = transactions[0]['account_id']
        for trade in trades:
            account_items[sample_account].append({
                'type': 'trade',
                'data': trade,
                'date': datetime.strptime(trade['trade_date'], '%Y-%m-%d %H:%M:%S'),
            })
    
    # Sort all items by date for each account
    for account_id in account_items:
        account_items[account_id].sort(key=lambda x: x['date'])
    
    # Process each account
    for account_id in sorted(account_items.keys()):
        items = account_items[account_id]
        running_balance = 0.0
        
        for item in items:
            debit_amount = 0.0
            credit_amount = 0.0
            projected_balance = running_balance
            transaction_id = None
            trade_id = None
            entry_type = None
            entry_date = None
            
            if item['type'] == 'transaction':
                trans = item['data']
                transaction_id = trans['transaction_id']
                transaction_type = trans['transaction_type']
                amount = trans['amount']
                entry_date = trans['date']
                entry_type = transaction_type
                
                if transaction_type in ['DEPOSIT', 'DIVIDEND', 'INTEREST']:
                    # Credits (increase balance)
                    credit_amount = amount
                    projected_balance = running_balance + amount
                elif transaction_type in ['WITHDRAWAL', 'FEE']:
                    # Debits (decrease balance)
                    debit_amount = amount
                    projected_balance = running_balance - amount
            
            elif item['type'] == 'trade':
                trade = item['data']
                trade_id = trade['trade_id']
                execution_id = trade['execution_id']
                trade_amount = trade['trade_price'] * trade['shares']
                entry_date = trade['trade_date']
                entry_type = 'TRADE_SETTLEMENT'
                
                # Determine buy/sell side
                order_id = execution_orders.get(execution_id)
                if order_id:
                    side = order_sides.get(order_id)
                    
                    if side == 'B':  # Buy
                        # Buys are debits (cash goes out)
                        debit_amount = trade_amount
                        projected_balance = running_balance - trade_amount
                    elif side == 'S':  # Sell
                        # Sells are credits (cash comes in)
                        credit_amount = trade_amount
                        projected_balance = running_balance + trade_amount
                    else:
                        continue
                else:
                    continue
                
                # Create transaction_id reference (use trade_id as placeholder)
                transaction_id = trade_id
            
            # Only add if balance remains non-negative
            if projected_balance >= 0:
                running_balance = projected_balance
                
                entry = {
                    'ledger_id': ledger_id,
                    'account_id': account_id,
                    'transaction_id': transaction_id,
                    'trade_id': trade_id,
                    'entry_type': entry_type,
                    'debit_amount': debit_amount,
                    'credit_amount': credit_amount,
                    'running_balance': running_balance,
                    'entry_date': entry_date,
                }
                
                ledger_entries.append(entry)
                ledger_id += 1
    
    return ledger_entries

def format_ledger_sql(ledger_entries):
    """Format ledger entries as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\cash_ledger_insert.sql",
        "-- Cash Ledger generated by cash_ledger_generator.py",
        "-- Execute this file in PostgreSQL to populate the Cash_Ledger table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(ledger_entries)}",
        "-- Table: Cash_Ledger (Ledger_ID, Account_ID, Transaction_ID, Trade_ID, Entry_Type, Debit_Amount, Credit_Amount, Running_Balance, Entry_Date)",
        "-- Entry_Type options: DEPOSIT, WITHDRAWAL, DIVIDEND, INTEREST, FEE, TRADE_SETTLEMENT",
        ""
    ]
    
    for entry in ledger_entries:
        trade_id_str = str(entry['trade_id']) if entry['trade_id'] else 'NULL'
        sql = f"INSERT INTO Cash_Ledger (Ledger_ID, Account_ID, Transaction_ID, Trade_ID, Entry_Type, Debit_Amount, Credit_Amount, Running_Balance, Entry_Date) VALUES ({entry['ledger_id']}, {entry['account_id']}, {entry['transaction_id']}, {trade_id_str}, '{entry['entry_type']}', {entry['debit_amount']}, {entry['credit_amount']}, {entry['running_balance']}, '{entry['entry_date']}');"
        sql_lines.append(sql)
    
    return "\n".join(sql_lines)

if __name__ == "__main__":
    # Parse input files
    orders_file = "orders_insert.sql"
    executions_file = "executions_insert.sql"
    trades_file = "trades_insert.sql"
    transactions_file = "transactions_insert.sql"
    
    print("Parsing orders file...")
    order_sides = parse_orders_file(orders_file)
    
    if order_sides is None:
        print("Error: Failed to parse orders file")
        exit(1)
    
    print("Parsing executions file...")
    execution_orders = parse_executions_file(executions_file)
    
    if execution_orders is None:
        print("Error: Failed to parse executions file")
        exit(1)
    
    print("Parsing trades file...")
    trades = parse_trades_file(trades_file)
    
    if trades is None:
        trades = []  # No trades is OK
    
    print("Parsing transactions file...")
    transactions = parse_transactions_file(transactions_file)
    
    if transactions is None:
        transactions = []  # No transactions is OK
    
    print(f"\nStarting cash ledger generation...")
    print(f"Orders with sides: {len(order_sides)}")
    print(f"Executions: {len(execution_orders)}")
    print(f"Trades: {len(trades)}")
    print(f"Transactions: {len(transactions)}")
    
    # Generate ledger entries
    ledger_entries = generate_ledger_entries(transactions, trades, order_sides, execution_orders)
    
    # Format as SQL
    sql_output = format_ledger_sql(ledger_entries)
    
    # Write to file
    output_path = "cash_ledger_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    # Calculate and display statistics
    total_debits = sum(e['debit_amount'] for e in ledger_entries)
    total_credits = sum(e['credit_amount'] for e in ledger_entries)
    
    # Count by entry type
    entry_types = {}
    for e in ledger_entries:
        etype = e['entry_type']
        entry_types[etype] = entry_types.get(etype, 0) + 1
    
    print(f"\nGenerated {len(ledger_entries)} ledger entries")
    print(f"\nEntry Type Distribution:")
    for etype in sorted(entry_types.keys()):
        print(f"  {etype}: {entry_types[etype]}")
    
    print(f"\nLedger Totals:")
    print(f"  Total Debits: ${total_debits:,.2f}")
    print(f"  Total Credits: ${total_credits:,.2f}")
    print(f"  Net: ${total_credits - total_debits:,.2f}")
    print(f"\nOutput written to {output_path}")
