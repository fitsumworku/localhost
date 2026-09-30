-- PaySprint Wealth Platform — Enterprise Schema
-- Pre-loaded, read-heavy schema used for exploration and query practice
-- across Modules 02-05 (and referenced again in Module 10).
-- Domain: a wealth management platform. Advisors manage clients; clients
-- hold one or more accounts; accounts hold instruments via transactions
-- and current holdings.

DROP TABLE IF EXISTS Audit_Logs;
DROP TABLE IF EXISTS Disputes;
DROP TABLE IF EXISTS Cash_Ledger;
DROP TABLE IF EXISTS Transactions;
DROP TABLE IF EXISTS Trades;
DROP TABLE IF EXISTS Executions;
DROP TABLE IF EXISTS Order_Reservations;
DROP TABLE IF EXISTS Orders;
DROP TABLE IF EXISTS Account_Positions;
DROP TABLE IF EXISTS Securities;
DROP TABLE IF EXISTS Accounts;
DROP TABLE IF EXISTS User_Roles;
DROP TABLE IF EXISTS Roles;
DROP TABLE IF EXISTS Users;












CREATE TABLE Users (
    User_ID BIGSERIAL PRIMARY KEY,
    Name VARCHAR(100) NOT NULL CHECK (length(btrim(Name)) > 0),
    Email VARCHAR(255) NOT NULL CHECK (Email = lower(btrim(Email)) AND length(Email) > 0),
    Password_Hash VARCHAR(255) NOT NULL CHECK (length(Password_Hash) > 0),

    Status VARCHAR(9) NOT NULL DEFAULT 'ACTIVE'
        CHECK (Status IN ('ACTIVE', 'SUSPENDED')),
    Created_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Updated_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Unique(Email),
    CHECK (Updated_Date >= Created_Date)
);

CREATE TABLE Roles(
    Role_ID BIGSERIAL PRIMARY KEY,
    Name VARCHAR(16) NOT NULL UNIQUE CHECK (Name IN ('CLIENT', 'ADMIN'))
);

CREATE TABLE User_Roles(
    User_ID BIGINT NOT NULL REFERENCES Users(User_ID),
    Role_ID BIGINT NOT NULL REFERENCES Roles(Role_ID),
    PRIMARY KEY (User_ID, Role_ID)
);

CREATE TABLE Accounts (
    Account_ID BIGSERIAL PRIMARY KEY,
    User_ID BIGINT NOT NULL REFERENCES Users(User_ID),

    Currency CHAR(3) NOT NULL DEFAULT 'USD' CHECK (Currency = 'USD'),
    Cash_Balance NUMERIC(20, 2) NOT NULL DEFAULT 0
        CHECK(Cash_balance >= 0 AND Cash_Balance < 'Infinity'::NUMERIC),
    Created_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Updated_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (Updated_Date >= Created_Date)
);

CREATE TABLE Securities(
    Security_ID BIGSERIAL PRIMARY KEY,
    Ticker VARCHAR(40) NOT NULL CHECK (length(btrim(Ticker)) > 0),
    Name VARCHAR(255) NOT NULL CHECK (length(btrim(Name)) > 0),
    Asset_Type VARCHAR(16) NOT NULL CHECK (Asset_Type IN ('EQUITY', 'FOREX', 'CRYPTO')),
    Exchange VARCHAR(80) NOT NULL CHECK (length(btrim(Exchange)) > 0),
    Quote_Currency CHAR(3) NOT NULL CHECK (Quote_Currency ~ '^[A-Z]{3}$'),
    Base_Currency CHAR(3),
    Status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (Status IN ('ACTIVE', 'HALTED', 'DELISTED')),
    Sector VARCHAR(100),
    UNIQUE (Ticker, Exchange),
    UNIQUE (Security_ID, Quote_Currency),
    CHECK (
        (Asset_Type = 'FOREX' AND Base_Currency IS NOT NULL 
           AND Base_Currency ~ '^[A-Z]{3}$' AND Base_Currency <> Quote_Currency)
        OR (Asset_Type <> 'FOREX' AND Base_Currency IS NULL)
    )
);

CREATE TABLE Account_Positions (
    Position_ID BIGSERIAL PRIMARY KEY, 
    Account_ID BIGINT NOT NULL REFERENCES Accounts(Account_ID),
    Security_ID BIGINT NOT NULL REFERENCES Securities(Security_ID),
    Quantity NUMERIC(28,12) NOT NULL DEFAULT 0
        CHECK (Quantity >= 0 AND Quantity < 'Infinity'::NUMERIC),
    Average_Price NUMERIC(28,12) NOT NULL DEFAULT 0
        CHECK (Average_Price >= 0 AND Average_Price < 'Infinity'::NUMERIC),
    Updated_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (Account_ID, Security_ID),
    CHECK (Quantity <> 0 OR Average_Price = 0)
);

CREATE TABLE Orders(
    Order_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL REFERENCES Accounts(Account_ID),
    Security_ID BIGINT NOT NULL REFERENCES Securities(Security_ID),
    Client_Request_ID UUID NOT NULL,
    Side CHAR(1) NOT NULL CHECK (Side IN ('B', 'S')),
    Requested_Amount NUMERIC(20,2),
    Quantity_Ordered NUMERIC(28,12),
    Order_Status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED'
        CHECK (Order_Status IN ('SUBMITTED', 'ACCEPTED', 'IN_EXECUTION', 'FILLED', 'REJECTED', 'CANCELLED')),
    Created_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Updated_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Accepted_At TIMESTAMPTZ,
    Terminal_At TIMESTAMPTZ,
    Rejection_Reason TEXT,
    UNIQUE (Account_ID, Client_Request_ID),
    UNIQUE (Order_ID, Account_ID, Security_ID, Side),
    CHECK (
        (Side = 'B' AND Requested_Amount IS NOT NULL AND Requested_Amount > 0
            AND Requested_Amount < 'Infinity'::NUMERIC AND Quantity_Ordered IS NULL)
        OR
        (Side = 'S' AND Quantity_Ordered IS NOT NULL AND Quantity_Ordered > 0
            AND Quantity_Ordered < 'Infinity'::NUMERIC AND Requested_Amount IS NULL)
    ),
    CHECK (Updated_Date >= Created_Date),
    CHECK (Accepted_At IS NULL OR Accepted_At >= Created_Date), 
    CHECK (Terminal_At IS NULL OR Terminal_At >= COALESCE(Accepted_At, Created_Date)),
    CHECK (Order_Status NOT IN ('ACCEPTED', 'IN_EXECUTION', 'FILLED', 'CANCELLED')
            OR Accepted_At IS NOT NULL),
    CHECK (Order_Status <> 'SUBMITTED' OR Accepted_At IS NULL),
    CHECK ((Order_Status IN ('FILLED', 'REJECTED', 'CANCELLED')) = (Terminal_At IS NOT NULL)),
    CHECK (
        (Order_Status = 'REJECTED' AND Rejection_Reason IS NOT NULL 
            AND length(btrim(Rejection_Reason)) > 0)
        OR (Order_Status <> 'REJECTED' AND Rejection_Reason IS NULL)
    )
);

CREATE TABLE Order_Reservations(
    Order_ID BIGINT PRIMARY KEY,
    Account_ID BIGINT NOT NULL,
    Security_ID BIGINT NOT NULL,
    Side CHAR(1) NOT NULL,
    Reserved_Cash NUMERIC(20,2),
    Reserved_Quantity NUMERIC(28,12),
    Status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE'
        CHECK (Status IN ('ACTIVE', 'CONSUMED', 'RELEASED')),
    Created_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Resolved_At TIMESTAMPTZ,
    FOREIGN KEY (Order_ID, Account_ID, Security_ID, Side)
        REFERENCES Orders(Order_ID, Account_ID, Security_ID, Side),
    CHECK (
        (Side = 'B' AND Reserved_Cash IS NOT NULL AND Reserved_Cash > 0
            AND Reserved_Cash < 'Infinity'::NUMERIC AND Reserved_Quantity IS NULL)
        OR 
        (Side = 'S' AND Reserved_Quantity IS NOT NULL AND Reserved_Quantity > 0
            AND Reserved_Quantity < 'Infinity'::NUMERIC AND Reserved_Cash IS NULL)
    ),
    CHECK ((Status = 'ACTIVE' AND Resolved_At IS NULL)
            OR (Status <> 'ACTIVE' AND Resolved_At IS NOT NULL AND Resolved_At >= Created_Date))
);


CREATE TABLE Executions(
    Execution_ID BIGSERIAL PRIMARY KEY,
    Order_ID BIGINT NOT NULL,
    Account_ID BIGINT NOT NULL,
    Security_ID BIGINT NOT NULL,
    Side CHAR(1) NOT NULL,
    Status_Of_Execution VARCHAR(8) NOT NULL 
        CHECK(Status_Of_Execution IN ('FILLED', 'REJECTED', 'FAILED')),
    Started_At TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Finished_At TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Quote_Price NUMERIC(28,12),
    Quote_Currency CHAR(3),
    Quote_Timestamp TIMESTAMPTZ,
    Quote_Source VARCHAR(100),

    FX_Rate_To_USD NUMERIC(28,12),
    FX_Quote_Timestamp TIMESTAMPTZ,
    FX_Source VARCHAR(100),
    Quantity_Filled NUMERIC(28,12),

    Price_Of_Execution NUMERIC(28,12),
    Cash_Amount Numeric(20,2) GENERATED ALWAYS AS 
        (round(Quantity_Filled * Price_Of_Execution, 2)) STORED,
    Failure_Reason TEXT,
    Exchange_Trade_ID VARCHAR(100),
    FOREIGN KEY(Order_ID, Account_ID, Security_ID, Side)
        REFERENCES Orders(Order_ID, Account_ID, Security_ID, Side),
    FOREIGN KEY (Security_ID, Quote_Currency)
        REFERENCES Securities(Security_ID, Quote_Currency),
    UNIQUE (Execution_ID, Account_ID),
    CHECK (Finished_At >= Started_At),

    CHECK (
            (Quote_Price IS NULL AND Quote_Currency IS NULL AND Quote_Timestamp IS NULL 
                AND Quote_Source IS NULL)
            OR  
            (Quote_Price IS NOT NULL AND Quote_Price > 0 AND Quote_Price < 'Infinity'::NUMERIC
                AND Quote_Currency IS NOT NULL AND Quote_Timestamp IS NOT NULL
                AND Quote_Source IS NOT NULL AND length(btrim(Quote_Source)) > 0
                AND Quote_Timestamp <= Finished_At)

    ),

    CHECK (FX_Rate_To_USD IS NULL OR
            (FX_Rate_To_USD > 0 AND FX_Rate_To_USD < 'Infinity'::NUMERIC)),
    CHECK (FX_Rate_To_USD IS NULL OR Quote_Price IS NOT NULL),
    CHECK (Quote_Currency IS DISTINCT FROM 'USD' OR FX_Rate_To_USD IS NULL OR FX_Rate_To_USD = 1),
    CHECK (
        (FX_Quote_Timestamp IS NULL AND FX_Source IS NULL)
        OR
        (FX_Quote_Timestamp IS NOT NULL AND FX_Source IS NOT NULL
            AND length(btrim(FX_Source)) > 0 AND FX_Quote_Timestamp <= Finished_At
            AND FX_Rate_To_USD IS NOT NULL)
    ),
    CHECK (
        (Status_Of_Execution = 'FILLED'
            AND Quantity_Filled IS NOT NULL AND Quantity_Filled > 0
            AND Quantity_Filled < 'Infinity'::NUMERIC
            AND Price_Of_Execution IS NOT NULL AND Price_Of_Execution > 0
            AND Price_Of_Execution < 'Infinity'::NUMERIC
            AND Cash_Amount IS NOT NULL AND Cash_Amount > 0
            AND Quote_Price IS NOT NULL AND FX_Rate_To_USD IS NOT NULL
            AND Price_Of_Execution = round(Quote_Price * FX_Rate_To_USD, 12)
            AND (Quote_Currency = 'USD' OR 
                (FX_Quote_Timestamp IS NOT NULL AND FX_Source IS NOT NULL))
            AND Failure_Reason IS NULL)
        OR
        (Status_Of_Execution IN ('REJECTED', 'FAILED')
            AND Quantity_Filled IS NULL AND Price_Of_Execution IS NULL
            AND Failure_Reason IS NOT NULL AND length(btrim(Failure_Reason)) > 0
            AND Exchange_Trade_ID IS NULL)
    )

);

CREATE UNIQUE INDEX uq_one_filled_Execution_per_order
    ON Executions(Order_ID) WHERE Status_Of_Execution = 'FILLED';
CREATE UNIQUE INDEX uq_exchange_trade_reference
    ON Executions(Quote_Source, Exchange_Trade_ID) WHERE Exchange_Trade_ID IS NOT NULL;


CREATE TABLE Trades(
    Trade_ID BIGSERIAL PRIMARY KEY,
    Execution_ID BIGINT NOT NULL UNIQUE,
    Account_ID BIGINT NOT NULL,
    Trade_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Settlement_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (Execution_ID, Account_ID) REFERENCES Executions(Execution_ID, Account_ID),
    UNIQUE (Trade_ID, Account_ID),
    CHECK (Settlement_Date >= Trade_Date)
);

CREATE TABLE Transactions(
    Transaction_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL REFERENCES Accounts(Account_ID),
    Client_Request_ID UUID NOT NULL,
    Transaction_Amount NUMERIC(20,2) NOT NULL
        CHECK (Transaction_Amount > 0 AND Transaction_Amount < 'Infinity'::NUMERIC),
    Transaction_Type VARCHAR(16) NOT NULL
        CHECK (Transaction_Type IN ('DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'INTEREST', 'FEE')),
    Transaction_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Transaction_Status VARCHAR(9) NOT NULL DEFAULT 'PENDING'
        CHECK (Transaction_Status IN ('PENDING', 'COMPLETED', 'FAILED')),
    Completed_At TIMESTAMPTZ,
    Failure_Reason TEXT,
    UNIQUE (Account_ID, Client_Request_ID),
    UNIQUE(Transaction_ID, Account_ID),
    CHECK((Transaction_Status = 'PENDING' AND Completed_At IS NULL)
            OR (Transaction_Status <> 'PENDING' AND Completed_At IS NOT NULL
                AND Completed_At >= Transaction_Date)),
    CHECK ((Transaction_Status = 'FAILED' AND Failure_Reason IS NOT NULL
            AND length(btrim(Failure_Reason)) > 0)
        OR (Transaction_Status <> 'FAILED' AND Failure_Reason IS NULL))
);

CREATE TABLE Cash_Ledger(
    Ledger_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL REFERENCES Accounts(Account_ID),
    Transaction_ID BIGINT UNIQUE,
    Trade_ID BIGINT UNIQUE,
    Entry_Type VARCHAR(16) NOT NULL 
        CHECK(Entry_Type IN ('DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'INTEREST', 'FEE', 'BUY', 'SELL')),
    Debit_Amount NUMERIC(20,2) NOT NULL DEFAULT 0
        CHECK (Debit_Amount >= 0 AND Debit_Amount < 'Infinity'::NUMERIC),
    Credit_Amount NUMERIC(20,2) NOT NULL DEFAULT 0
        CHECK (Credit_Amount >= 0 AND Credit_Amount < 'Infinity'::NUMERIC),
    Running_Balance NUMERIC(20,2) NOT NULL
        CHECK (Running_Balance >= 0 AND Running_Balance < 'Infinity'::NUMERIC),
    Entry_Date TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (Transaction_ID, Account_ID) REFERENCES Transactions(Transaction_ID, Account_ID),
    FOREIGN KEY (Trade_ID, Account_ID) REFERENCES Trades(Trade_ID, Account_ID),
    CHECK ((Transaction_ID IS NOT NULL) <> (Trade_ID IS NOT NULL)),
    CHECK ((Debit_Amount > 0 AND Credit_Amount = 0)
        OR (Credit_Amount > 0 AND Debit_Amount = 0)),
    CHECK (
        (Transaction_ID IS NOT NULL AND Entry_Type IN ('DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'INTEREST', 'FEE'))
        OR (Trade_ID IS NOT NULL AND Entry_Type IN ('BUY', 'SELL'))
    ),
    CHECK ((Entry_Type IN ('DEPOSIT', 'DIVIDEND', 'INTEREST', 'SELL') AND Debit_Amount > 0)
        OR (Entry_Type IN ('WITHDRAWAL', 'FEE', 'BUY') AND Credit_Amount > 0))
);

CREATE TABLE Disputes(
    Dispute_ID BIGSERIAL PRIMARY KEY,
    Account_ID BIGINT NOT NULL REFERENCES Accounts(Account_ID),
    Admin_ID BIGINT REFERENCES Users(User_ID),
    Transaction_ID BIGINT,
    Trade_ID BIGINT,
    Dispute_Type VARCHAR(50) NOT NULL CHECK (length(btrim(Dispute_Type)) > 0),
    Description Text,
    Status VARCHAR(12) NOT NULL DEFAULT 'OPEN'
        CHECK (Status IN ('OPEN', 'UNDER_REVIEW', 'ESCALATED', 'RESOLVED', 'REJECTED')),
    Date_Created TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    Date_Resolved TIMESTAMPTZ,
    FOREIGN KEY (Transaction_ID, Account_ID) REFERENCES Transactions(Transaction_ID, Account_ID),
    FOREIGN KEY (Trade_ID, Account_ID) REFERENCES Trades(Trade_ID, Account_ID),
    CHECK ((Transaction_ID IS NOT NULL) <> (Trade_ID IS NOT NULL)),
    CHECK ((Status IN ('RESOLVED', 'REJECTED') AND Date_Resolved IS NOT NULL
                AND Date_Resolved >= Date_Created)
            OR (Status NOT IN ('RESOLVED', 'REJECTED') AND Date_Resolved IS NULL))
);

CREATE TABLE Audit_Logs(
    Audit_ID BIGSERIAL PRIMARY KEY,
    Actor_User_ID BIGINT REFERENCES Users(User_ID),
    Actor_Type VARCHAR(6) NOT NULL CHECK (Actor_Type IN ('USER', 'SYSTEM')),
    Subject_User_ID BIGINT REFERENCES Users(User_ID),
    Affected_Table VARCHAR(100) NOT NULL,
    Record_Key JSONB NOT NULL,
    Action_Type VARCHAR(40) NOT NULL CHECK (length(btrim(Action_Type)) > 0),
    Old_Value JSONB,
    New_Value JSONB,
    Timestamp TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK ((Actor_Type = 'USER' AND Actor_User_ID IS NOT NULL)
    OR (Actor_Type = 'SYSTEM' AND Actor_User_ID is NULL)),
    CHECK(jsonb_typeof(Record_Key) = 'object')
);


