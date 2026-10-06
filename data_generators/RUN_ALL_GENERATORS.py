#!/usr/bin/env python3
"""
COMPREHENSIVE GENERATOR EXECUTION SCRIPT
Runs all data generators in correct sequence and produces validation report
"""

import subprocess
import sys
from pathlib import Path
from datetime import datetime

generators = [
    ("1. Roles", "roles_generation.py"),
    ("2. Users", "user_generation.py"),
    ("3. Accounts", "accounts_generation.py"),
    ("4. Cash Balances", "account_cash_balances_generator.py"),
    # ("5. Securities", "securities_generation.py"),  # SKIP: using securities_insert.sql from market API
    ("6. Orders", "orders_generation.py"),
    ("7. Order Reservations", "order_reservations_generator.py"),
    ("8. Executions/Trades/Transactions/CashLedger", "comprehensive_generator.py"),
    ("9. Account Positions & Disputes", "remaining_generators.py"),
]

print("=" * 80)
print("TRADING PLATFORM DATA GENERATION - COMPREHENSIVE EXECUTION")
print("=" * 80)
print(f"Start Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

results = []
base_path = Path(__file__).parent
data_folder = Path(__file__).parent.parent / "data"

# Create data folder if it doesn't exist
data_folder.mkdir(parents=True, exist_ok=True)

for name, script in generators:
    script_path = base_path / script
    
    if not script_path.exists():
        print(f"[SKIP] {name} - SKIPPED (file not found)")
        results.append((name, "SKIPPED", "File not found"))
        continue
    
    print(f"[RUN] Running {name}...")
    try:
        result = subprocess.run(
            [sys.executable, str(script_path)],
            cwd=base_path,
            capture_output=True,
            text=True,
            timeout=60
        )
        
        if result.returncode == 0:
            print(f"[OK] {name} - SUCCESS")
            results.append((name, "SUCCESS", result.stdout.split('\n')[-3] if result.stdout else ""))
            # Print last few lines of output
            output_lines = result.stdout.strip().split('\n')
            for line in output_lines[-3:]:
                if line.strip():
                    print(f"   {line}")
        else:
            print(f"[FAIL] {name} - FAILED")
            results.append((name, "FAILED", result.stderr[:100] if result.stderr else "Unknown error"))
            print(f"   Error: {result.stderr[:200]}")
    
    except subprocess.TimeoutExpired:
        print(f"[TIMEOUT] {name} - TIMEOUT")
        results.append((name, "TIMEOUT", "Execution took too long"))
    except Exception as e:
        print(f"[ERR] {name} - ERROR: {str(e)}")
        results.append((name, "ERROR", str(e)[:100]))
    
    print()

# Print summary
print("\n" + "=" * 80)
print("EXECUTION SUMMARY")
print("=" * 80)

for name, status, detail in results:
    status_symbol = "[OK]" if status == "SUCCESS" else "[FAIL]" if status == "FAILED" else "[SKIP]"
    print(f"{status_symbol} {name}: {status}")
    if detail and detail.strip():
        print(f"   {detail[:60]}")

print(f"\nEnd Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
print("=" * 80)

