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
    Currency_Key BIGSERIAL PRIMARY KEY,
    Currency_Code CHAR(3) NOT NULL UNIQUE,
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

CREATE TABLE DIM_USER (
    User_Key BIGSERIAL PRIMARY KEY,
    User_ID BIGINT NOT NULL UNIQUE,
    User_Name VARCHAR(100) NOT NULL,
    Status VARCHAR(20) NOT NULL,
    Is_Blacklisted CHAR(1) NOT NULL
);

CREATE TABLE DIM_ACCOUNT (
    Account_Key BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL UNIQUE,
    User_Key BIGINT NOT NULL,
    Account_Status VARCHAR(20) NOT NULL,
    Created_Date DATE NOT NULL,
    FOREIGN KEY (User_Key)
        REFERENCES DIM_USER(User_Key)
);

CREATE TABLE DIM_SECURITY (
    Security_Key BIGSERIAL PRIMARY KEY,
    Security_ID BIGINT NOT NULL UNIQUE,
    Ticker VARCHAR(40) NOT NULL,
    Company_Name VARCHAR(255) NOT NULL,
    Sector VARCHAR(100),
    Asset_Type VARCHAR(16) NOT NULL,
    Exchange VARCHAR(80) NOT NULL,
    Security_Status VARCHAR(16) NOT NULL,
    Quote_Currency_Key BIGINT NOT NULL,
    Base_Currency_Key BIGINT,
    FOREIGN KEY (Quote_Currency_Key) REFERENCES DIM_CURRENCY(Currency_Key),
    FOREIGN KEY (Base_Currency_Key) REFERENCES DIM_CURRENCY(Currency_Key),
    CHECK (Asset_Type IN ('EQUITY', 'FOREX', 'CRYPTO')),
    CHECK (Security_Status IN ('ACTIVE', 'HALTED', 'DELISTED')),
    CHECK ((Asset_Type = 'FOREX' AND Base_Currency_Key IS NOT NULL) OR (Asset_Type <> 'FOREX' AND Base_Currency_Key IS NULL))
);

CREATE TABLE FACT_ORDERS (
    Order_Fact_Key BIGSERIAL PRIMARY KEY,
    Order_ID BIGINT NOT NULL,
    Submission_Date_Key INTEGER NOT NULL,
    User_Key BIGINT NOT NULL,
    Account_Key BIGINT NOT NULL,
    Security_Key BIGINT NOT NULL,
    Order_Status_Key SMALLINT NOT NULL,
    Side CHAR(1) NOT NULL,
    Quantity_Ordered NUMERIC(28,12),
    Requested_Amount NUMERIC(20,2),
    Accepted_At_Date_Key INTEGER,
    Terminal_At_Date_Key INTEGER,
    Rejection_Reason TEXT,
    FOREIGN KEY (Submission_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (Accepted_At_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (Terminal_At_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (User_Key)
        REFERENCES DIM_USER(User_Key),
    FOREIGN KEY (Account_Key)
        REFERENCES DIM_ACCOUNT(Account_Key),
    FOREIGN KEY (Security_Key)
        REFERENCES DIM_SECURITY(Security_Key),
    FOREIGN KEY (Order_Status_Key)
        REFERENCES DIM_ORDER_STATUS(Order_Status_Key),
    CHECK (Side IN ('B', 'S'))
);

CREATE TABLE FACT_EXECUTIONS (
    Execution_Fact_Key BIGSERIAL PRIMARY KEY,
    Execution_ID BIGINT NOT NULL,
    Order_ID BIGINT NOT NULL,
    Started_At_Date_Key INTEGER NOT NULL,
    Finished_At_Date_Key INTEGER NOT NULL,
    User_Key BIGINT NOT NULL,
    Account_Key BIGINT NOT NULL,
    Security_Key BIGINT NOT NULL,
    Execution_Status_Key SMALLINT NOT NULL,
    Side CHAR(1) NOT NULL,
    Quantity_Filled NUMERIC(28,12),
    Price_Of_Execution NUMERIC(28,12),
    Cash_Amount NUMERIC(20,2),
    Quote_Price NUMERIC(28,12),
    Quote_Currency_Key BIGINT,
    FX_Rate_To_USD NUMERIC(28,12),
    Failure_Reason TEXT,
    Exchange_Trade_ID VARCHAR(100),
    FOREIGN KEY (Started_At_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (Finished_At_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (User_Key)
        REFERENCES DIM_USER(User_Key),
    FOREIGN KEY (Account_Key)
        REFERENCES DIM_ACCOUNT(Account_Key),
    FOREIGN KEY (Security_Key)
        REFERENCES DIM_SECURITY(Security_Key),
    FOREIGN KEY (Execution_Status_Key)
        REFERENCES DIM_EXECUTION_STATUS(Execution_Status_Key),
    FOREIGN KEY (Quote_Currency_Key)
        REFERENCES DIM_CURRENCY(Currency_Key),
    CHECK (Side IN ('B', 'S'))
);

CREATE TABLE FACT_TRADES (
    Trade_Fact_Key BIGSERIAL PRIMARY KEY,
    Trade_ID BIGINT NOT NULL,
    Execution_ID BIGINT NOT NULL,
    Trade_Date_Key INTEGER NOT NULL,
    Settlement_Date_Key INTEGER NOT NULL,
    User_Key BIGINT NOT NULL,
    Account_Key BIGINT NOT NULL,
    Security_Key BIGINT NOT NULL,
    Trade_Type CHAR(1) NOT NULL,
    Shares NUMERIC(28,12) NOT NULL,
    Trade_Price NUMERIC(28,12) NOT NULL,
    Trade_Value NUMERIC(20,2) NOT NULL,
    Status_Of_Trade VARCHAR(16),
    FOREIGN KEY (Trade_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (Settlement_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (User_Key)
        REFERENCES DIM_USER(User_Key),
    FOREIGN KEY (Account_Key)
        REFERENCES DIM_ACCOUNT(Account_Key),
    FOREIGN KEY (Security_Key)
        REFERENCES DIM_SECURITY(Security_Key)
);

CREATE TABLE FACT_TRANSACTIONS (
    Transaction_Fact_Key BIGSERIAL PRIMARY KEY,
    Transaction_ID BIGINT NOT NULL,
    Transaction_Date_Key INTEGER NOT NULL,
    Completed_At_Date_Key INTEGER,
    User_Key BIGINT NOT NULL,
    Account_Key BIGINT NOT NULL,
    Transaction_Type_Key SMALLINT NOT NULL,
    Transaction_Amount NUMERIC(20,2) NOT NULL,
    Transaction_Status VARCHAR(9) NOT NULL,
    Failure_Reason TEXT,
    FOREIGN KEY (Transaction_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (Completed_At_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (User_Key)
        REFERENCES DIM_USER(User_Key),
    FOREIGN KEY (Account_Key)
        REFERENCES DIM_ACCOUNT(Account_Key),
    FOREIGN KEY (Transaction_Type_Key)
        REFERENCES DIM_TRANSACTION_TYPE(Transaction_Type_Key),
    CHECK (Transaction_Status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE FACT_DISPUTES (
    Dispute_Fact_Key BIGSERIAL PRIMARY KEY,
    Dispute_ID BIGINT NOT NULL,
    Created_Date_Key INTEGER NOT NULL,
    Resolved_Date_Key INTEGER,
    User_Key BIGINT NOT NULL,
    Admin_Key BIGINT,
    Account_Key BIGINT NOT NULL,
    Dispute_Status_Key SMALLINT NOT NULL,
    Is_Transaction_Dispute BOOLEAN NOT NULL,
    Dispute_Type VARCHAR(50) NOT NULL,
    Description TEXT,
    FOREIGN KEY (Created_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (Resolved_Date_Key)
        REFERENCES DIM_DATE(Date_Key),
    FOREIGN KEY (User_Key)
        REFERENCES DIM_USER(User_Key),
    FOREIGN KEY (Admin_Key)
        REFERENCES DIM_USER(User_Key),
    FOREIGN KEY (Account_Key)
        REFERENCES DIM_ACCOUNT(Account_Key),
    FOREIGN KEY (Dispute_Status_Key)
        REFERENCES DIM_DISPUTE_STATUS(Dispute_Status_Key)
);

CREATE INDEX ix_dim_currency_code ON DIM_CURRENCY(Currency_Code);
CREATE INDEX ix_dim_order_status_code ON DIM_ORDER_STATUS(Order_Status_Code);
CREATE INDEX ix_dim_transaction_type_code ON DIM_TRANSACTION_TYPE(Transaction_Type_Code);
CREATE INDEX ix_dim_dispute_status_code ON DIM_DISPUTE_STATUS(Dispute_Status_Code);
CREATE INDEX ix_dim_execution_status_code ON DIM_EXECUTION_STATUS(Execution_Status_Code);
CREATE INDEX ix_dim_security_quote_currency ON DIM_SECURITY(Quote_Currency_Key);
CREATE INDEX ix_dim_security_base_currency ON DIM_SECURITY(Base_Currency_Key);
CREATE INDEX ix_fact_orders_status ON FACT_ORDERS(Order_Status_Key);
CREATE INDEX ix_fact_orders_account ON FACT_ORDERS(Account_Key, Submission_Date_Key DESC);
CREATE INDEX ix_fact_executions_order ON FACT_EXECUTIONS(Order_ID);
CREATE INDEX ix_fact_executions_status ON FACT_EXECUTIONS(Execution_Status_Key);
CREATE INDEX ix_fact_executions_account ON FACT_EXECUTIONS(Account_Key, Started_At_Date_Key DESC);
CREATE INDEX ix_fact_trades_account ON FACT_TRADES(Account_Key, Trade_Date_Key DESC);
CREATE INDEX ix_fact_trades_execution ON FACT_TRADES(Execution_ID);
CREATE INDEX ix_fact_transactions_account ON FACT_TRANSACTIONS(Account_Key, Transaction_Date_Key DESC);
CREATE INDEX ix_fact_transactions_type ON FACT_TRANSACTIONS(Transaction_Type_Key);
CREATE INDEX ix_fact_disputes_status ON FACT_DISPUTES(Dispute_Status_Key);
CREATE INDEX ix_fact_disputes_account ON FACT_DISPUTES(Account_Key, Created_Date_Key DESC);
CREATE INDEX ix_fact_disputes_admin ON FACT_DISPUTES(Admin_Key) WHERE Admin_Key IS NOT NULL;
