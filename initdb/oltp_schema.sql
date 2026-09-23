-- PaySprint Wealth Platform — Enterprise Schema
-- Pre-loaded, read-heavy schema used for exploration and query practice
-- across Modules 02-05 (and referenced again in Module 10).
-- Domain: a wealth management platform. Advisors manage clients; clients
-- hold one or more accounts; accounts hold instruments via transactions
-- and current holdings.

DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Roles;
DROP TABLE IF EXISTS User_Roles;
DROP TABLE IF EXISTS Audit_Logs;
DROP TABLE IF EXISTS Accounts;
DROP TABLE IF EXISTS Securities;
DROP TABLE IF EXISTS Account_Positions;
DROP TABLE IF EXISTS Orders;
DROP TABLE IF EXISTS Executions;
DROP TABLE IF EXISTS Trades;
DROP TABLE IF EXISTS Transactions;
DROP TABLE IF EXISTS Disputes;
DROP TABLE IF EXISTS Cash_Ledger;

CREATE TABLE Users (
    User_ID BIGSERIAL PRIMARY KEY,
    Name VARCHAR(100) NOT NULL,
    Email VARCHAR(255) NOT NULL UNIQUE,
    Password VARCHAR(255) NOT NULL,
    Created_Date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Status VARCHAR(9) NOT NULL,
    CONSTRAINT chk_users_status CHECK (Status IN ('ACTIVE','SUSPENDED')),
);

CREATE TABLE Roles (
    Role_ID BIGSERIAL PRIMARY KEY,
    Name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE User_Roles (
    User_ID BIGINT NOT NULL,
    Role_ID BIGINT NOT NULL,
    PRIMARY KEY (User_ID, Role_ID),
    FOREIGN KEY (User_ID) REFERENCES Users(User_ID),
    FOREIGN KEY (Role_ID) REFERENCES Roles(Role_ID)
);

CREATE TABLE Audit_Logs (
    Audit_ID BIGSERIAL PRIMARY KEY,
    User_ID BIGINT NOT NULL,
    Affected_Table VARCHAR(100) NOT NULL,
    Record_ID BIGINT NOT NULL,
    -- record id used to indicate the primary key of the affected record in the affected table
    Action_Type VARCHAR(6) NOT NULL,
    Old_Value TEXT,
    New_Value TEXT,
    Timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (User_ID) REFERENCES Users(User_ID),
    CONSTRAINT chk_audit_logs_action_type CHECK (Action_Type IN ('INSERT','UPDATE','DELETE'))
);

CREATE TABLE Accounts (
    Account_ID BIGSERIAL PRIMARY KEY,
    User_ID BIGINT NOT NULL,
    Created_Date TIMESTAMP NOT NULL,
    Status VARCHAR(9) NOT NULL,
    FOREIGN KEY (User_ID) REFERENCES Users(User_ID),
    CONSTRAINT chk_accounts_status CHECK (Status IN ('ACTIVE','SUSPENDED'))
);

CREATE TABLE Securities (
    Security_ID BIGSERIAL PRIMARY KEY,
    Ticker VARCHAR(20) NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Asset_Type VARCHAR(50) NOT NULL,
    Exchange VARCHAR(50) NOT NULL,
    Status VARCHAR(8) NOT NULL,
    Sector VARCHAR(100) NOT NULL,
    UNIQUE (Ticker, Exchange),
    CONSTRAINT chk_security_status CHECK (Status IN ('ACTIVE','HALTED','DELISTED'))
);

CREATE TABLE Account_Positions (
    Position_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL,
    Security_ID BIGINT NOT NULL,
    Total_Shares NUMERIC(18,4),
    Average_Price NUMERIC(18,4),
    Updated_Date TIMESTAMP,
    FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
    FOREIGN KEY (Security_ID) REFERENCES Securities(Security_ID),
    UNIQUE (Account_ID, Security_ID),
    CHECK (Total_Shares >= 0),
    CHECK (Average_Price >= 0)
);

CREATE TABLE Orders (
    Order_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL,
    Security_ID BIGINT NOT NULL,
    Side CHAR(1) NOT NULL,
    Order_Type VARCHAR(10) NOT NULL,
    Quantity_Ordered NUMERIC(18,4) NOT NULL,
    Limit_Price NUMERIC(18,4),
    Order_Status VARCHAR(16) NOT NULL,
    Created_Date TIMESTAMP NOT NULL,
    Updated_Date TIMESTAMP NOT NULL,
    FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
    FOREIGN KEY (Security_ID) REFERENCES Securities(Security_ID),
    CHECK (Side IN ('B','S')),
    CHECK (Quantity_Ordered > 0),
    CHECK (Order_Type IN ('MARKET','LIMIT','STOP','STOP_LIMIT')),
    CHECK (Order_Status IN ('PENDING','IN_EXECUTION','CANCELLED')),
    CHECK (Limit_Price IS NULL AND Order_Type<>'LIMIT' OR Limit_Price > 0),
    CHECK (Updated_Date >= Created_Date)
);

CREATE TABLE Executions (
    Execution_ID BIGSERIAL PRIMARY KEY,
    Order_ID BIGINT NOT NULL,
    Quantity_Filled NUMERIC(18,4) NOT NULL,
    Price_Of_Execution NUMERIC(18,4) NOT NULL,
    Date_Of_Execution TIMESTAMP NOT NULL,
    Settlement_Date TIMESTAMP,
    Status_Of_Execution VARCHAR(20) NOT NULL,
    Exchange_Trade_ID VARCHAR(100) UNIQUE,
    FOREIGN KEY (Order_ID) REFERENCES Orders(Order_ID),
    CHECK (Quantity_Filled > 0),
    CHECK (Price_Of_Execution > 0),
    CHECK (Status_Of_Execution IN ('PENDING','FILLED','PARTIALLY_FILLED','FAILED')),
    CHECK (Settlement_Date >= Date_Of_Execution)
);

CREATE TABLE Trades (
    Trade_ID BIGSERIAL PRIMARY KEY,
    Execution_ID BIGINT NOT NULL,
    Trade_Price NUMERIC(18,4) NOT NULL,
    Shares NUMERIC(18,4) NOT NULL,
    Trade_Status VARCHAR(18) NOT NULL,
    Trade_Date TIMESTAMP NOT NULL,
    FOREIGN KEY (Execution_ID) REFERENCES Executions(Execution_ID),
    FOREIGN KEY (Security_ID) REFERENCES Securities(Security_ID),
    CHECK (Trade_Price > 0),
    CHECK (Shares > 0),
    CHECK (Trade_Status IN ('PENDING','SETTLED','DISPUTED','REVERSED'))
);

CREATE TABLE Transactions (
    Transaction_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL,
    Transaction_Amount NUMERIC(18,4) NOT NULL,
    Transaction_Type VARCHAR(16) NOT NULL,
    Transaction_Date TIMESTAMP NOT NULL,
    Transaction_Status VARCHAR(9) NOT NULL,
    FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
    CHECK (Transaction_Amount > 0),
    CHECK (Transaction_Type IN ('DEPOSIT','WITHDRAWAL','DIVIDEND','INTEREST','FEE')),
    CHECK (Transaction_Status IN ('PENDING','COMPLETED','FAILED','DISPUTED','REVERSED'))
);

CREATE TABLE Disputes (
    Dispute_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL,
    Admin_ID BIGINT NOT NULL,
    Transaction_ID BIGINT NOT NULL,
    Dispute_Type VARCHAR(50) NOT NULL,
    Description TEXT,
    Status VARCHAR(12) NOT NULL,
    Date_Created TIMESTAMP NOT NULL,
    Date_Resolved TIMESTAMP,
    FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
    FOREIGN KEY (Admin_ID) REFERENCES Users(User_ID),
    FOREIGN KEY (Transaction_ID) REFERENCES Transactions(Transaction_ID),
    FOREIGN KEY (Trade_ID) REFERENCES Trades(Trade_ID),
    CHECK (Status IN ('OPEN','UNDER_REVIEW','ESCALATED','RESOLVED','REJECTED')),
    CHECK (Date_Resolved IS NULL OR Date_Resolved >= Date_Created)
);

CREATE TABLE Cash_Ledger (
    Ledger_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL,
    Transaction_ID BIGINT,
    Trade_ID BIGINT,
    Entry_Type VARCHAR(16) NOT NULL,
    Debit_Amount NUMERIC(18,4) NOT NULL,
    -- positive values (sell or deposit transactions)
    Credit_Amount NUMERIC(18,4) NOT NULL,
    -- negative values (buy or withdrawal transactions)
    Running_Balance NUMERIC(18,4) NOT NULL,
    -- running balance = previous balance - Credit_Amount + Debit_Amount
    Holdings NUMERIC(18,4),
    -- any amount being held for pending trades or other obligations
    Entry_Date TIMESTAMP NOT NULL,
    FOREIGN KEY (Account_ID) REFERENCES Accounts(Account_ID),
    FOREIGN KEY (Transaction_ID) REFERENCES Transactions(Transaction_ID),
    FOREIGN KEY (Trade_ID) REFERENCES Trades(Trade_ID),
    CHECK (Debit_Amount >= 0),
    CHECK (Credit_Amount >= 0),
    CHECK (Running_Balance >= 0),
    CHECK (Entry_Type IN ('DEPOSIT','WITHDRAWAL','DIVIDEND','INTEREST','FEE','TRADE_SETTLEMENT')),
    CHECK (((Debit_Amount > 0 AND Credit_Amount = 0) OR (Credit_Amount > 0 AND Debit_Amount = 0)))
);