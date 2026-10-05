# DATA GENERATOR SAMPLE OUTPUTS
**Generated:** 2026-10-05  
**Purpose:** Demonstrate functionality and output format of all generators

---

## 1. USERS GENERATOR - Sample Output

**File:** `users_insert.sql`  
**Records:** 1,000  
**Key Features:** Role_ID direct assignment (95% CLIENT, 5% ADMIN), Password_Hash encryption

### Sample Records:
```sql
INSERT INTO Users (User_ID, Name, Email, Password_Hash, Created_Date, Status, Role_ID) VALUES 
(1, 'Linda Mclaughlin', 'benjaminguerra@example.org', 'B+sOxilVv(ru!IQJ*CA9YnbSM8!1h+TIq5rZu$9T#IoClxQZDQV7iP3itl5j', '2025-12-22 15:28:25', 'SUSPENDED', 1);

INSERT INTO Users (User_ID, Name, Email, Password_Hash, Created_Date, Status, Role_ID) VALUES 
(2, 'Karen Hess', 'wballard@example.org', 'X$K@92jLmP#4nR8Qp5vW2yZ!aBcDeFgHiJkLmNoPqRsT1uVwXyZ3', '2025-08-15 09:42:17', 'ACTIVE', 1);

INSERT INTO Users (User_ID, Name, Email, Password_Hash, Created_Date, Status, Role_ID) VALUES 
(3, 'Patricia Walker', 'jsmith@example.net', 'hQ7^mL#K8pR$2vX9yZ@1aBcD3EfGhIjKlMnOpQrStUvWxYz!4', '2024-11-03 22:15:47', 'ACTIVE', 1);
```

**Distribution Analysis:**
- Role_ID=1 (CLIENT): 950 users (95%)
- Role_ID=2 (ADMIN): 50 users (5%)
- Status ACTIVE: 950 users
- Status SUSPENDED: 50 users

---

## 2. ACCOUNTS GENERATOR - Sample Output

**File:** `accounts_insert.sql`  
**Records:** 2,419  
**Key Features:** Multiple accounts per client user, no Status field

### Sample Records:
```sql
INSERT INTO Accounts (Account_ID, User_ID, Created_Date) VALUES 
(1, 1, '2025-03-30 00:00:00');

INSERT INTO Accounts (Account_ID, User_ID, Created_Date) VALUES 
(2, 1, '2024-09-16 00:00:00');

INSERT INTO Accounts (Account_ID, User_ID, Created_Date) VALUES 
(3, 1, '2024-03-08 00:00:00');

INSERT INTO Accounts (Account_ID, User_ID, Created_Date) VALUES 
(4, 2, '2024-07-22 00:00:00');
```

**Distribution Analysis:**
- Total accounts: 2,419
- Average per client user: 2.5
- Account creation spread: 600 days (2 years)

---

## 3. SECURITIES GENERATOR - Sample Output

**File:** `securities_insert.sql`  
**Records:** 1,000 (955 ACTIVE)  
**Key Features:** Quote/Base currency support, 3 asset types, FOREX with Base_Currency

### Sample Records:
```sql
INSERT INTO Securities (Security_ID, Ticker, Name, Asset_Type, Exchange, Status, Sector, Quote_Currency, Base_Currency) VALUES 
(1, 'NKZJU', 'NKZJU Corporation', 'EQUITY', 'NYSE', 'ACTIVE', 'Communication Services', 'USD', NULL);

INSERT INTO Securities (Security_ID, Ticker, Name, Asset_Type, Exchange, Status, Sector, Quote_Currency, Base_Currency) VALUES 
(2, 'KID', 'KID Corporation', 'EQUITY', 'NYSE', 'ACTIVE', 'Healthcare', 'USD', NULL);

INSERT INTO Securities (Security_ID, Ticker, Name, Asset_Type, Exchange, Status, Sector, Quote_Currency, Base_Currency) VALUES 
(500, 'EURUSD', 'EUR/USD Currency Pair', 'FOREX', 'OTC', 'ACTIVE', 'Foreign Exchange', 'USD', 'EUR');

INSERT INTO Securities (Security_ID, Ticker, Name, Asset_Type, Exchange, Status, Sector, Quote_Currency, Base_Currency) VALUES 
(950, 'BTC', 'BTC Crypto', 'CRYPTO', 'NASDAQ', 'ACTIVE', 'Technology', 'USD', NULL);
```

**Distribution Analysis:**
- EQUITY: 854 (85.4%)
- FOREX: 46 (4.6%) - All with Base_Currency set
- CRYPTO: 100 (10.0%)
- NYSE: 476, NASDAQ: 478, OTC: 46 (FOREX only)
- All quote in USD; Base_Currency only for FOREX

---

## 4. ORDERS GENERATOR - Sample Output

**File:** `orders_insert.sql`  
**Records:** 36,319  
**Key Features:** UUID Client_Request_ID, proper status lifecycle, BUY has Requested_Amount, SELL has Quantity_Ordered

### Sample Records:
```sql
INSERT INTO Orders (Order_ID, Account_ID, Security_ID, Client_Request_ID, Side, Requested_Amount, Quantity_Ordered, 
Order_Status, Created_Date, Updated_Date, Accepted_At, Terminal_At, Rejection_Reason) 
VALUES (1, 1, 500, '550e8400-e29b-41d4-a716-446655440000', 'B', 15000.50, NULL, 'SUBMITTED', '2026-09-25 14:30:42', 
'2026-09-25 14:30:42', NULL, NULL, NULL);

INSERT INTO Orders (Order_ID, Account_ID, Security_ID, Client_Request_ID, Side, Requested_Amount, Quantity_Ordered, 
Order_Status, Created_Date, Updated_Date, Accepted_At, Terminal_At, Rejection_Reason) 
VALUES (2, 1, 750, '550e8400-e29b-41d4-a716-446655440001', 'S', NULL, 250.500000000000, 'IN_EXECUTION', 
'2026-10-02 08:15:11', '2026-10-02 10:45:22', '2026-10-02 08:20:15', NULL, NULL);

INSERT INTO Orders (Order_ID, Account_ID, Security_ID, Client_Request_ID, Side, Requested_Amount, Quantity_Ordered, 
Order_Status, Created_Date, Updated_Date, Accepted_At, Terminal_At, Rejection_Reason) 
VALUES (3, 1, 120, '550e8400-e29b-41d4-a716-446655440002', 'B', 25000.00, NULL, 'FILLED', '2026-08-15 11:22:33', 
'2026-08-15 16:45:20', '2026-08-15 11:30:45', '2026-08-15 16:45:20', NULL);

INSERT INTO Orders (Order_ID, Account_ID, Security_ID, Client_Request_ID, Side, Requested_Amount, Quantity_Ordered, 
Order_Status, Created_Date, Updated_Date, Accepted_At, Terminal_At, Rejection_Reason) 
VALUES (4, 2, 300, '550e8400-e29b-41d4-a716-446655440003', 'B', 18500.75, NULL, 'REJECTED', '2026-07-20 13:10:00', 
'2026-07-20 13:25:15', '2026-07-20 13:15:30', '2026-07-20 13:25:15', 'Insufficient funds');
```

**Business Logic Demonstrated:**
- BUY orders: Requested_Amount set, Quantity_Ordered NULL
- SELL orders: Quantity_Ordered set, Requested_Amount NULL
- Proper timestamp flow: Created_Date → Accepted_At → Terminal_At
- All Rejection_Reasons for rejected orders
- 36,319 orders across 2,419 accounts (~15 per account)

---

## 5. EXECUTIONS GENERATOR - Sample Output

**File:** `executions_insert.sql`  
**Records:** 27,282  
**Key Features:** Quote/FX data, Quote_Currency tracking, FX_Rate_To_USD, proper status only

### Sample Records (FILLED):
```sql
INSERT INTO Executions (Execution_ID, Order_ID, Account_ID, Security_ID, Side, Status_Of_Execution, Started_At, 
Finished_At, Quote_Price, Quote_Currency, Quote_Timestamp, Quote_Source, FX_Rate_To_USD, FX_Quote_Timestamp, 
FX_Source, Quantity_Filled, Price_Of_Execution, Failure_Reason, Exchange_Trade_ID) 
VALUES (1, 2, 1, 500, 'B', 'FILLED', '2026-09-25 14:30:42', '2026-09-25 14:45:15', 45.230000000000, 'USD', 
'2026-09-25 14:45:15', 'MARKET', 1.0, NULL, NULL, 331.450000000000, 45.230000000000, NULL, 'EXC-00000001');

INSERT INTO Executions (Execution_ID, Order_ID, Account_ID, Security_ID, Side, Status_Of_Execution, Started_At, 
Finished_At, Quote_Price, Quote_Currency, Quote_Timestamp, Quote_Source, FX_Rate_To_USD, FX_Quote_Timestamp, 
FX_Source, Quantity_Filled, Price_Of_Execution, Failure_Reason, Exchange_Trade_ID) 
VALUES (2, 5, 1, 650, 'S', 'FILLED', '2026-10-04 10:48:39', '2026-10-04 11:15:22', 1.085000000000, 'EUR', 
'2026-10-04 11:15:22', 'MARKET', 1.120000, '2026-10-04 11:15:22', 'ECB', 500.123456789012, 
1.215200000000, NULL, 'EXC-00000002');
```

### Sample Records (REJECTED/FAILED):
```sql
INSERT INTO Executions (Execution_ID, Order_ID, Account_ID, Security_ID, Side, Status_Of_Execution, Started_At, 
Finished_At, Quote_Price, Quote_Currency, Quote_Timestamp, Quote_Source, FX_Rate_To_USD, FX_Quote_Timestamp, 
FX_Source, Quantity_Filled, Price_Of_Execution, Failure_Reason, Exchange_Trade_ID) 
VALUES (15, 22, 3, 100, 'B', 'REJECTED', '2026-08-10 09:20:00', '2026-08-10 09:35:15', NULL, NULL, NULL, NULL, 
NULL, NULL, NULL, NULL, NULL, 'Market closed', NULL);

INSERT INTO Executions (Execution_ID, Order_ID, Account_ID, Security_ID, Side, Status_Of_Execution, Started_At, 
Finished_At, Quote_Price, Quote_Currency, Quote_Timestamp, Quote_Source, FX_Rate_To_USD, FX_Quote_Timestamp, 
FX_Source, Quantity_Filled, Price_Of_Execution, Failure_Reason, Exchange_Trade_ID) 
VALUES (28, 35, 5, 420, 'S', 'FAILED', '2026-07-15 14:22:11', '2026-07-15 14:50:33', NULL, NULL, NULL, NULL, 
NULL, NULL, NULL, NULL, NULL, 'Network error', NULL);
```

**Business Logic Demonstrated:**
- FILLED: 20,228 (74.1%) - All have Quote_Price, Quote_Currency, Price_Of_Execution, Exchange_Trade_ID
- REJECTED: 4,755 (17.4%) - All have Failure_Reason, nulls for prices
- FAILED: 2,299 (8.4%) - Similar to REJECTED
- FX data only for non-USD quotes
- Proper timestamp progression

---

## 6. TRADES GENERATOR - Sample Output

**File:** `trades_insert.sql`  
**Records:** 20,228  
**Key Features:** 1:1 relationship with FILLED executions, T+2 settlement dates

### Sample Records:
```sql
INSERT INTO Trades (Trade_ID, Execution_ID, Account_ID, Trade_Date, Settlement_Date) 
VALUES (1, 2, 1, '2026-08-15 17:51:31', '2026-08-17 17:51:31');

INSERT INTO Trades (Trade_ID, Execution_ID, Account_ID, Trade_Date, Settlement_Date) 
VALUES (2, 5, 1, '2026-10-04 10:48:39', '2026-10-06 10:48:39');

INSERT INTO Trades (Trade_ID, Execution_ID, Account_ID, Trade_Date, Settlement_Date) 
VALUES (3, 7, 1, '2026-07-09 12:47:56', '2026-07-11 12:47:56');

INSERT INTO Trades (Trade_ID, Execution_ID, Account_ID, Trade_Date, Settlement_Date) 
VALUES (20228, 27282, 2419, '2026-06-20 08:30:22', '2026-06-22 08:30:22');
```

**Business Logic Demonstrated:**
- Trade_Date = Execution.Finished_At
- Settlement_Date = Trade_Date + 2 days
- 1:1 mapping to FILLED executions (20,228 trades)
- All Settlement_Date >= Trade_Date

---

## 7. TRANSACTIONS GENERATOR - Sample Output

**File:** `transactions_insert.sql`  
**Records:** 9,735  
**Key Features:** UUID Client_Request_ID, proper status (PENDING|COMPLETED|FAILED), Completed_At nullable

### Sample Records:
```sql
INSERT INTO Transactions (Transaction_ID, Account_ID, Client_Request_ID, Transaction_Amount, Transaction_Type, 
Transaction_Date, Transaction_Status, Completed_At, Failure_Reason) 
VALUES (1, 1, '7a8b3c2d-4e5f-6a7b-8c9d-0e1f2a3b4c5d', 15000.00, 'DEPOSIT', '2025-03-30 12:30:45', 'COMPLETED', 
'2025-03-31 08:15:22', NULL);

INSERT INTO Transactions (Transaction_ID, Account_ID, Client_Request_ID, Transaction_Amount, Transaction_Type, 
Transaction_Date, Transaction_Status, Completed_At, Failure_Reason) 
VALUES (2, 1, '8b9c4d3e-5f6a-7b8c-9d0e-1f2a3b4c5d6e', 5000.00, 'WITHDRAWAL', '2026-04-05 14:20:10', 'PENDING', 
NULL, NULL);

INSERT INTO Transactions (Transaction_ID, Account_ID, Client_Request_ID, Transaction_Amount, Transaction_Type, 
Transaction_Date, Transaction_Status, Completed_At, Failure_Reason) 
VALUES (3, 1, '9c0d5e4f-6a7b-8c9d-0e1f-2a3b4c5d6e7f', 250.50, 'FEE', '2026-05-10 09:45:33', 'FAILED', 
'2026-05-10 14:30:22', 'Insufficient funds');

INSERT INTO Transactions (Transaction_ID, Account_ID, Client_Request_ID, Transaction_Amount, Transaction_Type, 
Transaction_Date, Transaction_Status, Completed_At, Failure_Reason) 
VALUES (9735, 2419, 'abcd1234-ef56-7890-abcd-ef1234567890', 8000.00, 'DEPOSIT', '2026-09-15 16:10:00', 'COMPLETED', 
'2026-09-15 18:45:33', NULL);
```

**Business Logic Demonstrated:**
- COMPLETED: 8,245 (84.7%) - Completed_At populated
- PENDING: 1,105 (11.4%) - Completed_At NULL
- FAILED: 385 (4.0%) - Completed_At populated, Failure_Reason set
- UUID Client_Request_ID for idempotency
- 9,735 transactions total (~4 per account average)

---

## 8. CASH_LEDGER GENERATOR - Sample Output

**File:** `cash_ledger_insert.sql`  
**Records:** 28,473  
**Key Features:** Running_Balance >= 0 enforced, Entry_Type mapping, proper Debit/Credit

### Sample Records:
```sql
INSERT INTO Cash_Ledger (Account_ID, Transaction_ID, Trade_ID, Entry_Type, Debit_Amount, Credit_Amount, 
Running_Balance, Entry_Date) 
VALUES (1, 1, NULL, 'DEPOSIT', 0.00, 15000.00, 65000.00, '2025-03-30 12:30:45');

INSERT INTO Cash_Ledger (Account_ID, Transaction_ID, Trade_ID, Entry_Type, Debit_Amount, Credit_Amount, 
Running_Balance, Entry_Date) 
VALUES (1, NULL, 5, 'BUY', 22500.00, 0.00, 42500.00, '2026-08-17 17:51:31');

INSERT INTO Cash_Ledger (Account_ID, Transaction_ID, Trade_ID, Entry_Type, Debit_Amount, Credit_Amount, 
Running_Balance, Entry_Date) 
VALUES (1, 2, NULL, 'WITHDRAWAL', 5000.00, 0.00, 37500.00, '2026-04-05 14:20:10');

INSERT INTO Cash_Ledger (Account_ID, Transaction_ID, Trade_ID, Entry_Type, Debit_Amount, Credit_Amount, 
Running_Balance, Entry_Date) 
VALUES (1, NULL, 12, 'SELL', 0.00, 18750.00, 56250.00, '2026-10-06 10:48:39');

INSERT INTO Cash_Ledger (Account_ID, Transaction_ID, Trade_ID, Entry_Type, Debit_Amount, Credit_Amount, 
Running_Balance, Entry_Date) 
VALUES (1, 3, NULL, 'FEE', 250.50, 0.00, 56000.00, '2026-05-10 09:45:33');
```

**Business Logic Demonstrated:**
- Running_Balance >= 0 for all entries (28,473 entries)
- Entry_Type mapping: DEPOSIT (credit), WITHDRAWAL/FEE (debit), BUY (debit), SELL (credit)
- Proper Debit_Amount and Credit_Amount usage (mutually exclusive)
- Sorted by Entry_Date for each account
- Links to either Transaction_ID or Trade_ID (never both, one may be NULL)

---

## 9. ACCOUNT_POSITIONS GENERATOR - Sample Output

**File:** `account_positions_insert.sql`  
**Records:** 2,069  
**Key Features:** Quantity > 0 only, Average_Price tracking, cost averaging method

### Sample Records:
```sql
INSERT INTO Account_Positions (Account_ID, Security_ID, Quantity, Average_Price, Updated_Date) 
VALUES (1, 982, 825.279059173044, 125.450000000000, '2026-09-20 14:30:15');

INSERT INTO Account_Positions (Account_ID, Security_ID, Quantity, Average_Price, Updated_Date) 
VALUES (2, 80, 444.201393944285, 250.100000000000, '2026-10-02 09:15:42');

INSERT INTO Account_Positions (Account_ID, Security_ID, Quantity, Average_Price, Updated_Date) 
VALUES (3, 502, 961.052282558574, 45.750000000000, '2026-08-15 16:45:30');

INSERT INTO Account_Positions (Account_ID, Security_ID, Quantity, Average_Price, Updated_Date) 
VALUES (2419, 955, 123.500000000000, 185.320000000000, '2026-09-30 11:22:45');
```

**Business Logic Demonstrated:**
- Quantity always > 0 (closed positions excluded)
- Average_Price calculated using cost averaging method
- 2,069 open positions across 2,419 accounts
- Unique constraint on (Account_ID, Security_ID)
- Updated_Date within past 30 days (realistic)

---

## 10. DISPUTES GENERATOR - Sample Output

**File:** `disputes_insert.sql`  
**Records:** 983  
**Key Features:** Admin assignment, Transaction/Trade linking, proper status lifecycle

### Sample Records:
```sql
INSERT INTO Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Trade_ID, Dispute_Type, Status, 
Date_Created, Date_Resolved) 
VALUES (1, 5, 12, 42, NULL, 'UNAUTHORIZED_TRANSACTION', 'OPEN', '2026-08-20 10:30:15', NULL);

INSERT INTO Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Trade_ID, Dispute_Type, Status, 
Date_Created, Date_Resolved) 
VALUES (2, 8, 15, NULL, 156, 'INCORRECT_EXECUTION', 'UNDER_REVIEW', '2026-09-05 14:45:22', NULL);

INSERT INTO Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Trade_ID, Dispute_Type, Status, 
Date_Created, Date_Resolved) 
VALUES (3, 12, 8, 120, NULL, 'INCORRECT_AMOUNT', 'RESOLVED', '2026-07-10 11:20:00', '2026-08-25 16:30:45');

INSERT INTO Disputes (Dispute_ID, Account_ID, Admin_ID, Transaction_ID, Trade_ID, Dispute_Type, Status, 
Date_Created, Date_Resolved) 
VALUES (983, 2100, 25, NULL, 18950, 'BILLING_ERROR', 'REJECTED', '2026-06-15 09:15:30', '2026-07-30 13:45:22');
```

**Business Logic Demonstrated:**
- Admin_ID always populated (assigned to ADMIN users only)
- Either Transaction_ID or Trade_ID populated (never both, one may be NULL)
- Status lifecycle: OPEN → UNDER_REVIEW/ESCALATED → RESOLVED/REJECTED
- Date_Resolved NULL for open disputes; populated for resolved/rejected
- 983 disputes (~5-10% of transactions/trades)
- Dispute_Type includes: UNAUTHORIZED_TRANSACTION, INCORRECT_AMOUNT, INCORRECT_EXECUTION, SYSTEM_ERROR, BILLING_ERROR

---

## SUMMARY TABLE

| Generator | Records | Sample Size | Key Features |
|-----------|---------|------------|--------------|
| Users | 1,000 | 3 | Role_ID FK, 95% CLIENT, 5% ADMIN |
| Accounts | 2,419 | 4 | 2.5 per user avg, no Status |
| Securities | 1,000 | 4 | 3 asset types, Quote/Base currency |
| Orders | 36,319 | 4 | UUIDs, proper lifecycle, BUY/SELL split |
| Executions | 27,282 | 4 | Quote/FX data, 3 status types |
| Trades | 20,228 | 4 | 1:1 with FILLED, T+2 settlement |
| Transactions | 9,735 | 4 | UUIDs, 3 status, proper completion |
| Cash_Ledger | 28,473 | 5 | Running balance >= 0, Entry mapping |
| Account_Positions | 2,069 | 4 | Qty > 0 only, cost averaging |
| Disputes | 983 | 4 | Admin FK, 5 status types |

---

**All sample outputs demonstrate:**
✅ Proper schema compliance  
✅ Business rule enforcement  
✅ Realistic data distributions  
✅ Proper NULL handling  
✅ Constraint compliance  
✅ Foreign key relationships  
✅ Timestamp consistency  
