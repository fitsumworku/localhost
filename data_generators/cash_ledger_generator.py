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
        
        # Pattern: VALUES (order_id, account_id, security_id, 'side', 'order_type', quantity, limit_price, 'status', 'created_date', 'updated_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*(\d+),\s*([^,]*),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            order_id, account_id, security_id, side, order_type, quantity, limit_price, status, created_date, updated_date = match
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
        
        # Pattern: VALUES (execution_id, order_id, quantity_filled, price, 'time', 'settlement_date', 'status', 'exchange_id')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*(\d+),\s*([^,]*),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            execution_id, order_id, quantity_filled, price, time_of_execution, settlement_date, status, exchange_id = match
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
    """Parse trades_insert.sql to create trade_id -> execution_id mapping"""
    trade_executions = {}
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (trade_id, execution_id, trade_price, shares, 'status', 'trade_date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*(\d+),\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            trade_id, execution_id, trade_price, shares, status, trade_date = match
            trade_executions[int(trade_id)] = int(execution_id)
        
        print(f"Parsed {len(trade_executions)} trades with execution mappings")
        
    except FileNotFoundError:
        print(f"Warning: {filepath} not found.")
        return None
    except Exception as e:
        print(f"Error parsing trades file: {e}")
        return None
    
    return trade_executions if trade_executions else None

def parse_transactions_file(filepath):
    """Parse transactions_insert.sql to extract all transactions"""
    transactions = []
    
    try:
        with open(filepath, 'r') as f:
            content = f.read()
        
        # Pattern: VALUES (transaction_id, account_id, trade_id, 'transaction_type', amount, 'status', 'date')
        pattern = r"VALUES\s*\((\d+),\s*(\d+),\s*([^,]*),\s*'([^']*)',\s*([^,]*),\s*'([^']*)',\s*'([^']*)'\s*\)"
        
        matches = re.findall(pattern, content)
        for match in matches:
            transaction_id, account_id, trade_id_str, transaction_type, amount, status, trans_date = match
            
            trade_id = None
            if trade_id_str != 'NULL':
                trade_id = int(trade_id_str)
            
            try:
                amount_float = float(amount)
            except ValueError:
                amount_float = 0.0
            
            transactions.append({
                'transaction_id': int(transaction_id),
                'account_id': int(account_id),
                'trade_id': trade_id,
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

def get_trade_side(trade_id, trade_executions, execution_orders, order_sides):
    """Determine if a trade is a buy or sell by following the chain"""
    if trade_id not in trade_executions:
        return None
    
    execution_id = trade_executions[trade_id]
    
    if execution_id not in execution_orders:
        return None
    
    order_id = execution_orders[execution_id]
    
    if order_id not in order_sides:
        return None
    
    return order_sides[order_id]

def generate_ledger_entries(transactions, trade_executions, execution_orders, order_sides):
    """Generate cash ledger entries from transactions, excluding trades that would create negative balance"""
    ledger_entries = []
    ledger_id = 1
    skipped_trades = 0
    
    # Group transactions by account
    account_transactions = defaultdict(list)
    for trans in transactions:
        account_transactions[trans['account_id']].append(trans)
    
    # Sort transactions per account: first deposit first, then rest by date
    for account_id in account_transactions:
        account_trans = account_transactions[account_id]
        
        # Find the first DEPOSIT transaction (initial deposit)
        deposits = [t for t in account_trans if t['transaction_type'] == 'DEPOSIT']
        other_trans = [t for t in account_trans if t['transaction_type'] != 'DEPOSIT']
        
        # Sort deposits and other transactions by date
        if deposits:
            initial_deposit = min(deposits, key=lambda t: t['date'])
            # Other deposits after initial
            other_deposits = [d for d in deposits if d != initial_deposit]
            other_deposits.sort(key=lambda t: t['date'])
            
            # Combine: initial deposit first, then all others sorted by date
            remaining_trans = other_trans + other_deposits
            remaining_trans.sort(key=lambda t: t['date'])
            
            account_transactions[account_id] = [initial_deposit] + remaining_trans
        else:
            # No deposits, just sort by date
            account_trans.sort(key=lambda t: t['date'])
            account_transactions[account_id] = account_trans
    
    # Process each account
    for account_id in sorted(account_transactions.keys()):
        account_trans = account_transactions[account_id]
        running_balance = 0.0
        
        for trans in account_trans:
            transaction_id = trans['transaction_id']
            transaction_type = trans['transaction_type']
            amount = trans['amount']
            trans_date = trans['date']
            
            debit_amount = 0.0
            credit_amount = 0.0
            projected_balance = running_balance
            
            if transaction_type == 'DEPOSIT':
                # Deposits are credits (increase balance)
                credit_amount = amount
                projected_balance = running_balance + amount
            
            elif transaction_type == 'WITHDRAWAL':
                # Withdrawals are debits (decrease balance)
                debit_amount = amount
                projected_balance = running_balance - amount
            
            elif transaction_type == 'TRADE_SETTLEMENT':
                # For trades, we need to determine if it's a buy or sell
                trade_id = trans['trade_id']
                side = get_trade_side(trade_id, trade_executions, execution_orders, order_sides)
                
                if side == 'B':  # Buy
                    # Buys are debits (cash goes out)
                    debit_amount = amount
                    projected_balance = running_balance - amount
                elif side == 'S':  # Sell
                    # Sells are credits (cash comes in)
                    credit_amount = amount
                    projected_balance = running_balance + amount
                else:
                    # If we can't determine side, skip this trade
                    skipped_trades += 1
                    continue
                
                # Skip this trade if it would result in negative balance
                if projected_balance < 0:
                    skipped_trades += 1
                    continue
            
            # Only add if balance remains non-negative
            if projected_balance >= 0:
                running_balance = projected_balance
                
                entry = {
                    'ledger_id': ledger_id,
                    'account_id': account_id,
                    'transaction_id': transaction_id,
                    'entry_type': transaction_type,
                    'debit_amount': debit_amount,
                    'credit_amount': credit_amount,
                    'running_balance': running_balance,
                    'entry_date': trans_date,
                }
                
                ledger_entries.append(entry)
                ledger_id += 1
    
    print(f"Skipped {skipped_trades} trades that would create negative balance")
    return ledger_entries

def format_ledger_sql(ledger_entries):
    """Format ledger entries as SQL INSERT statements"""
    sql_lines = [
        "-- filepath: c:\\Users\\Administrator\\Downloads\\data_generator\\cash_ledger_insert.sql",
        "-- Cash Ledger generated by cash_ledger_generator.py",
        "-- Execute this file in PostgreSQL to populate the Cash_Ledger table",
        f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"-- Total records: {len(ledger_entries)}",
        "-- Table: Cash_Ledger (Ledger_ID, Account_ID, Transaction_ID, Entry_Type, Debit_Amount, Credit_Amount, Running_Balance, Entry_Date)",
        ""
    ]
    
    for entry in ledger_entries:
        sql = f"INSERT INTO Cash_Ledger (Ledger_ID, Account_ID, Transaction_ID, Entry_Type, Debit_Amount, Credit_Amount, Running_Balance, Entry_Date) VALUES ({entry['ledger_id']}, {entry['account_id']}, {entry['transaction_id']}, '{entry['entry_type']}', {entry['debit_amount']}, {entry['credit_amount']}, {entry['running_balance']}, '{entry['entry_date']}');"
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
    trade_executions = parse_trades_file(trades_file)
    
    if trade_executions is None:
        print("Error: Failed to parse trades file")
        exit(1)
    
    print("Parsing transactions file...")
    transactions = parse_transactions_file(transactions_file)
    
    if transactions is None:
        print("Error: Failed to parse transactions file")
        exit(1)
    
    print(f"\nStarting cash ledger generation...")
    print(f"Orders with sides: {len(order_sides)}")
    print(f"Executions: {len(execution_orders)}")
    print(f"Trades: {len(trade_executions)}")
    print(f"Transactions: {len(transactions)}")
    
    # Generate ledger entries
    ledger_entries = generate_ledger_entries(transactions, trade_executions, execution_orders, order_sides)
    
    # Format as SQL
    sql_output = format_ledger_sql(ledger_entries)
    
    # Write to file
    output_path = "cash_ledger_insert.sql"
    with open(output_path, 'w') as f:
        f.write(sql_output)
    
    # Calculate and display statistics
    total_debits = sum(e['debit_amount'] for e in ledger_entries)
    total_credits = sum(e['credit_amount'] for e in ledger_entries)
    deposits = sum(1 for e in ledger_entries if e['entry_type'] == 'DEPOSIT')
    withdrawals = sum(1 for e in ledger_entries if e['entry_type'] == 'WITHDRAWAL')
    trades = sum(1 for e in ledger_entries if e['entry_type'] == 'TRADE_SETTLEMENT')
    
    print(f"\nGenerated {len(ledger_entries)} ledger entries")
    print(f"\nEntry Type Distribution:")
    print(f"  DEPOSIT: {deposits}")
    print(f"  WITHDRAWAL: {withdrawals}")
    print(f"  TRADE_SETTLEMENT: {trades}")
    print(f"\nLedger Totals:")
    print(f"  Total Debits: ${total_debits:,.2f}")
    print(f"  Total Credits: ${total_credits:,.2f}")
    print(f"  Net: ${total_credits - total_debits:,.2f}")
    print(f"\nOutput written to {output_path}")
