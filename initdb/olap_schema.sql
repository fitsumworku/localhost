-- PaySprint reporting database, schema version 1.
-- Run only against the separate OLAP database: this development reset is destructive.
-- ETL performs a full, atomic snapshot refresh. Dimensions contain CURRENT values
-- (Type 1): sector/role/status changes reclassify historical facts at the next refresh.
-- Keys are supplied by ETL; source-backed keys equal their original OLTP IDs.
-- All *_At timestamps are instants (TIMESTAMPTZ). Date keys use their UTC date.
-- Trade_Value, Trade_Price, Cash_Amount and Requested_Amount are USD values.
BEGIN;

DROP TABLE IF EXISTS FACT_DISPUTES;
DROP TABLE IF EXISTS FACT_EXECUTIONS;
DROP TABLE IF EXISTS FACT_ORDERS;
DROP TABLE IF EXISTS FACT_TRANSACTIONS;
DROP TABLE IF EXISTS FACT_TRADES;
DROP TABLE IF EXISTS DIM_SECURITY;
DROP TABLE IF EXISTS DIM_ACCOUNT;
DROP TABLE IF EXISTS DIM_USER;
DROP TABLE IF EXISTS DIM_EXECUTION_STATUS;
DROP TABLE IF EXISTS DIM_DISPUTE_STATUS;
DROP TABLE IF EXISTS DIM_TRANSACTION_TYPE;
DROP TABLE IF EXISTS DIM_ORDER_STATUS;
DROP TABLE IF EXISTS DIM_CURRENCY;
DROP TABLE IF EXISTS DIM_DATE;
DROP TABLE IF EXISTS ETL_METADATA;

CREATE TABLE ETL_METADATA (
    Metadata_ID SMALLINT PRIMARY KEY CHECK (Metadata_ID = 1),
    Schema_Version INTEGER NOT NULL CHECK (Schema_Version = 1),
    Source_Snapshot_At TIMESTAMPTZ,
    Last_Successful_At TIMESTAMPTZ,
    Duration_Ms BIGINT CHECK (Duration_Ms >= 0),
    Refresh_Count BIGINT NOT NULL DEFAULT 0 CHECK (Refresh_Count >= 0),
    Row_Counts JSONB NOT NULL DEFAULT '{}'::JSONB
        CHECK (jsonb_typeof(Row_Counts) = 'object')
);
INSERT INTO ETL_METADATA (Metadata_ID, Schema_Version) VALUES (1, 1);

CREATE TABLE DIM_DATE (
    Date_Key INTEGER PRIMARY KEY,
    Full_Date DATE NOT NULL UNIQUE,
    Day_Number SMALLINT NOT NULL,
    Day_Name VARCHAR(10) NOT NULL,
    Week_Number SMALLINT NOT NULL,
    Month_Number SMALLINT NOT NULL,
    Month_Name VARCHAR(15) NOT NULL,
    Quarter_Number SMALLINT NOT NULL,
    Year_Number SMALLINT NOT NULL,
    Is_Weekend BOOLEAN NOT NULL
);

CREATE TABLE DIM_CURRENCY (
    Currency_Key BIGINT PRIMARY KEY,
    Currency_Code CHAR(3) NOT NULL UNIQUE CHECK (Currency_Code ~ '^[A-Z]{3}$'),
    Currency_Name VARCHAR(50) NOT NULL,
    Country VARCHAR(100),
    Is_Base_Currency BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE DIM_ORDER_STATUS (
    Order_Status_Key SMALLINT PRIMARY KEY,
    Order_Status_Code VARCHAR(16) NOT NULL UNIQUE,
    Order_Status_Name VARCHAR(50) NOT NULL,
    Is_Terminal BOOLEAN NOT NULL,
    Is_Active BOOLEAN NOT NULL,
    CHECK (Order_Status_Code IN ('SUBMITTED', 'ACCEPTED', 'IN_EXECUTION', 'FILLED', 'REJECTED', 'CANCELLED'))
);

CREATE TABLE DIM_TRANSACTION_TYPE (
    Transaction_Type_Key SMALLINT PRIMARY KEY,
    Transaction_Type_Code VARCHAR(16) NOT NULL UNIQUE,
    Transaction_Type_Name VARCHAR(50) NOT NULL,
    Is_Cash_Inflow BOOLEAN NOT NULL,
    Is_Cash_Outflow BOOLEAN NOT NULL,
    Category VARCHAR(20) NOT NULL,
    CHECK (Transaction_Type_Code IN ('DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'INTEREST', 'FEE'))
);

CREATE TABLE DIM_DISPUTE_STATUS (
    Dispute_Status_Key SMALLINT PRIMARY KEY,
    Dispute_Status_Code VARCHAR(12) NOT NULL UNIQUE,
    Dispute_Status_Name VARCHAR(50) NOT NULL,
    Is_Terminal BOOLEAN NOT NULL,
    Is_Escalated BOOLEAN NOT NULL,
    CHECK (Dispute_Status_Code IN ('OPEN', 'UNDER_REVIEW', 'ESCALATED', 'RESOLVED', 'REJECTED'))
);

CREATE TABLE DIM_EXECUTION_STATUS (
    Execution_Status_Key SMALLINT PRIMARY KEY,
    Execution_Status_Code VARCHAR(8) NOT NULL UNIQUE,
    Execution_Status_Name VARCHAR(50) NOT NULL,
    Is_Terminal BOOLEAN NOT NULL DEFAULT TRUE,
    Is_Successful BOOLEAN NOT NULL,
    CHECK (Execution_Status_Code IN ('FILLED', 'REJECTED', 'FAILED'))
);

-- Includes admins so disputes can reference them, but client dashboard counts
-- filter Role_Name = 'CLIENT'. No email, password hash or JWT data is copied.
CREATE TABLE DIM_USER (
    User_Key BIGINT PRIMARY KEY,
    User_ID BIGINT NOT NULL UNIQUE,
    User_Name VARCHAR(100) NOT NULL,
    Role_Name VARCHAR(16) NOT NULL CHECK (Role_Name IN ('CLIENT', 'ADMIN')),
    Status VARCHAR(9) NOT NULL CHECK (Status IN ('ACTIVE', 'SUSPENDED')),
    Is_Blacklisted BOOLEAN NOT NULL,
    Created_At TIMESTAMPTZ NOT NULL,
    Updated_At TIMESTAMPTZ NOT NULL,
    CHECK (Is_Blacklisted = (Status = 'SUSPENDED'))
);

CREATE TABLE DIM_ACCOUNT (
    Account_Key BIGINT PRIMARY KEY,
    Account_ID BIGINT NOT NULL UNIQUE,
    User_Key BIGINT NOT NULL REFERENCES DIM_USER(User_Key),
    Currency_Key BIGINT NOT NULL REFERENCES DIM_CURRENCY(Currency_Key),
    Created_Date DATE NOT NULL,
    Created_At TIMESTAMPTZ NOT NULL,
    Updated_At TIMESTAMPTZ NOT NULL
);

CREATE TABLE DIM_SECURITY (
    Security_Key BIGINT PRIMARY KEY,
    Security_ID BIGINT NOT NULL UNIQUE,
    Ticker VARCHAR(40) NOT NULL,
    Company_Name VARCHAR(255) NOT NULL,
    Sector VARCHAR(100),
    Asset_Type VARCHAR(16) NOT NULL,
    Exchange VARCHAR(80) NOT NULL,
    Security_Status VARCHAR(16) NOT NULL,
    Quote_Currency_Key BIGINT NOT NULL REFERENCES DIM_CURRENCY(Currency_Key),
    Base_Currency_Key BIGINT REFERENCES DIM_CURRENCY(Currency_Key),
    UNIQUE (Ticker, Exchange),
    CHECK (Asset_Type IN ('EQUITY', 'ETF', 'FOREX', 'CRYPTO')),
    CHECK (Security_Status IN ('ACTIVE', 'HALTED', 'DELISTED')),
    CHECK ((Asset_Type = 'FOREX' AND Base_Currency_Key IS NOT NULL)
        OR (Asset_Type <> 'FOREX' AND Base_Currency_Key IS NULL))
);

-- One row per order, including rejected/cancelled/unfilled orders.
-- Status is the order's CURRENT state, not an event history.
CREATE TABLE FACT_ORDERS (
    Order_Fact_Key BIGINT PRIMARY KEY,
    Order_ID BIGINT NOT NULL UNIQUE,
    Submission_Date_Key INTEGER NOT NULL REFERENCES DIM_DATE(Date_Key),
    User_Key BIGINT NOT NULL REFERENCES DIM_USER(User_Key),
    Account_Key BIGINT NOT NULL REFERENCES DIM_ACCOUNT(Account_Key),
    Security_Key BIGINT NOT NULL REFERENCES DIM_SECURITY(Security_Key),
    Order_Status_Key SMALLINT NOT NULL REFERENCES DIM_ORDER_STATUS(Order_Status_Key),
    Side CHAR(1) NOT NULL CHECK (Side IN ('B', 'S')),
    Quantity_Ordered NUMERIC(28,12),
    Requested_Amount NUMERIC(20,2),
    Accepted_At_Date_Key INTEGER REFERENCES DIM_DATE(Date_Key),
    Terminal_At_Date_Key INTEGER REFERENCES DIM_DATE(Date_Key),
    Submitted_At TIMESTAMPTZ NOT NULL,
    Updated_At TIMESTAMPTZ NOT NULL,
    Accepted_At TIMESTAMPTZ,
    Terminal_At TIMESTAMPTZ,
    Rejection_Reason TEXT,
    CHECK ((Side = 'B' AND Requested_Amount > 0 AND Requested_Amount IS NOT NULL AND Quantity_Ordered IS NULL)
        OR (Side = 'S' AND Quantity_Ordered > 0 AND Quantity_Ordered IS NOT NULL AND Requested_Amount IS NULL))
);

-- One row per completed attempt. Failed attempts are NOT trading volume.
CREATE TABLE FACT_EXECUTIONS (
    Execution_Fact_Key BIGINT PRIMARY KEY,
    Execution_ID BIGINT NOT NULL UNIQUE,
    Order_ID BIGINT NOT NULL,
    Started_At_Date_Key INTEGER NOT NULL REFERENCES DIM_DATE(Date_Key),
    Finished_At_Date_Key INTEGER NOT NULL REFERENCES DIM_DATE(Date_Key),
    User_Key BIGINT NOT NULL REFERENCES DIM_USER(User_Key),
    Account_Key BIGINT NOT NULL REFERENCES DIM_ACCOUNT(Account_Key),
    Security_Key BIGINT NOT NULL REFERENCES DIM_SECURITY(Security_Key),
    Execution_Status_Key SMALLINT NOT NULL REFERENCES DIM_EXECUTION_STATUS(Execution_Status_Key),
    Side CHAR(1) NOT NULL CHECK (Side IN ('B', 'S')),
    Quantity_Filled NUMERIC(28,12),
    Price_Of_Execution NUMERIC(28,12),
    Cash_Amount NUMERIC(20,2),
    Quote_Price NUMERIC(28,12),
    Quote_Currency_Key BIGINT REFERENCES DIM_CURRENCY(Currency_Key),
    FX_Rate_To_USD NUMERIC(28,12),
    Started_At TIMESTAMPTZ NOT NULL,
    Finished_At TIMESTAMPTZ NOT NULL,
    Failure_Reason TEXT,
    Exchange_Trade_ID VARCHAR(100),
    CHECK (Finished_At >= Started_At)
);

-- One row per successful trade, never one row per execution attempt.
-- USD notional = the source execution's already-rounded Cash_Amount.
-- Shares are units: quantities across different instruments are not additive.
CREATE TABLE FACT_TRADES (
    Trade_Fact_Key BIGINT PRIMARY KEY,
    Trade_ID BIGINT NOT NULL UNIQUE,
    Execution_ID BIGINT NOT NULL UNIQUE,
    Order_ID BIGINT NOT NULL,
    Trade_Date_Key INTEGER NOT NULL REFERENCES DIM_DATE(Date_Key),
    Settlement_Date_Key INTEGER NOT NULL REFERENCES DIM_DATE(Date_Key),
    User_Key BIGINT NOT NULL REFERENCES DIM_USER(User_Key),
    Account_Key BIGINT NOT NULL REFERENCES DIM_ACCOUNT(Account_Key),
    Security_Key BIGINT NOT NULL REFERENCES DIM_SECURITY(Security_Key),
    Trade_Type CHAR(1) NOT NULL CHECK (Trade_Type IN ('B', 'S')),
    Shares NUMERIC(28,12) NOT NULL CHECK (Shares > 0),
    Trade_Price NUMERIC(28,12) NOT NULL CHECK (Trade_Price > 0),
    Trade_Value NUMERIC(20,2) NOT NULL CHECK (Trade_Value > 0),
    Traded_At TIMESTAMPTZ NOT NULL,
    Settled_At TIMESTAMPTZ NOT NULL,
    CHECK (Settled_At >= Traded_At)
);

CREATE TABLE FACT_TRANSACTIONS (
    Transaction_Fact_Key BIGINT PRIMARY KEY,
    Transaction_ID BIGINT NOT NULL UNIQUE,
    Transaction_Date_Key INTEGER NOT NULL REFERENCES DIM_DATE(Date_Key),
    Completed_At_Date_Key INTEGER REFERENCES DIM_DATE(Date_Key),
    User_Key BIGINT NOT NULL REFERENCES DIM_USER(User_Key),
    Account_Key BIGINT NOT NULL REFERENCES DIM_ACCOUNT(Account_Key),
    Transaction_Type_Key SMALLINT NOT NULL REFERENCES DIM_TRANSACTION_TYPE(Transaction_Type_Key),
    Transaction_Amount NUMERIC(20,2) NOT NULL CHECK (Transaction_Amount > 0),
    Transaction_Status VARCHAR(9) NOT NULL CHECK (Transaction_Status IN ('PENDING', 'COMPLETED', 'FAILED')),
    Transaction_At TIMESTAMPTZ NOT NULL,
    Completed_At TIMESTAMPTZ,
    Failure_Reason TEXT
);

CREATE TABLE FACT_DISPUTES (
    Dispute_Fact_Key BIGINT PRIMARY KEY,
    Dispute_ID BIGINT NOT NULL UNIQUE,
    Created_Date_Key INTEGER NOT NULL REFERENCES DIM_DATE(Date_Key),
    Resolved_Date_Key INTEGER REFERENCES DIM_DATE(Date_Key),
    User_Key BIGINT NOT NULL REFERENCES DIM_USER(User_Key),
    Admin_Key BIGINT REFERENCES DIM_USER(User_Key),
    Account_Key BIGINT NOT NULL REFERENCES DIM_ACCOUNT(Account_Key),
    Dispute_Status_Key SMALLINT NOT NULL REFERENCES DIM_DISPUTE_STATUS(Dispute_Status_Key),
    Transaction_ID BIGINT,
    Trade_ID BIGINT,
    Is_Transaction_Dispute BOOLEAN NOT NULL,
    Dispute_Type VARCHAR(50) NOT NULL,
    Description TEXT,
    Created_At TIMESTAMPTZ NOT NULL,
    Resolved_At TIMESTAMPTZ,
    CHECK ((Transaction_ID IS NOT NULL) <> (Trade_ID IS NOT NULL)),
    CHECK (Is_Transaction_Dispute = (Transaction_ID IS NOT NULL))
);

-- Unique constraints already index source IDs/codes. Add reporting access paths.
CREATE INDEX ix_dim_user_role_status ON DIM_USER(Role_Name, Status);
CREATE INDEX ix_dim_account_user ON DIM_ACCOUNT(User_Key);
CREATE INDEX ix_dim_security_sector ON DIM_SECURITY(Sector);
CREATE INDEX ix_fact_orders_submitted ON FACT_ORDERS(Submitted_At);
CREATE INDEX ix_fact_orders_status ON FACT_ORDERS(Order_Status_Key, Submitted_At);
CREATE INDEX ix_fact_orders_account ON FACT_ORDERS(Account_Key, Submitted_At DESC);
CREATE INDEX ix_fact_executions_order ON FACT_EXECUTIONS(Order_ID);
CREATE INDEX ix_fact_executions_status ON FACT_EXECUTIONS(Execution_Status_Key, Finished_At);
CREATE INDEX ix_fact_executions_account ON FACT_EXECUTIONS(Account_Key, Started_At DESC);
CREATE INDEX ix_fact_trades_time ON FACT_TRADES(Traded_At);
CREATE INDEX ix_fact_trades_account ON FACT_TRADES(Account_Key, Traded_At DESC);
CREATE INDEX ix_fact_trades_user ON FACT_TRADES(User_Key, Traded_At);
CREATE INDEX ix_fact_trades_security ON FACT_TRADES(Security_Key, Traded_At);
CREATE INDEX ix_fact_transactions_account ON FACT_TRANSACTIONS(Account_Key, Transaction_At DESC);
CREATE INDEX ix_fact_transactions_type ON FACT_TRANSACTIONS(Transaction_Type_Key);
CREATE INDEX ix_fact_disputes_status ON FACT_DISPUTES(Dispute_Status_Key);
CREATE INDEX ix_fact_disputes_account ON FACT_DISPUTES(Account_Key, Created_At DESC);
CREATE INDEX ix_fact_disputes_admin ON FACT_DISPUTES(Admin_Key) WHERE Admin_Key IS NOT NULL;

COMMIT;
