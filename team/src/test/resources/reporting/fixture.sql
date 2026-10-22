BEGIN;
SET LOCAL TIME ZONE 'UTC';
INSERT INTO users (user_id,name,email,password_hash,role_id,status,created_date,updated_date) VALUES
(1,'Test Admin','admin@etl.test','$2a$10$29jzeobe9uBxD3.0TObtO.gaaaBYY9vXeaZcOEb3lZ7Tjn4Q7rbxq',2,'ACTIVE','2025-01-01Z','2025-01-01Z'),
(2,'Alice Client','alice@etl.test','$2a$10$29jzeobe9uBxD3.0TObtO.gaaaBYY9vXeaZcOEb3lZ7Tjn4Q7rbxq',1,'ACTIVE','2025-01-01Z','2025-01-01Z'),
(3,'Bob Client','bob@etl.test','$2a$10$29jzeobe9uBxD3.0TObtO.gaaaBYY9vXeaZcOEb3lZ7Tjn4Q7rbxq',1,'ACTIVE','2025-01-01Z','2025-01-01Z'),
(4,'Carol Blacklisted','carol@etl.test','$2a$10$29jzeobe9uBxD3.0TObtO.gaaaBYY9vXeaZcOEb3lZ7Tjn4Q7rbxq',1,'SUSPENDED','2025-01-01Z','2026-01-12Z'),
(5,'Dana No Trading','dana@etl.test','$2a$10$29jzeobe9uBxD3.0TObtO.gaaaBYY9vXeaZcOEb3lZ7Tjn4Q7rbxq',1,'ACTIVE','2025-01-01Z','2025-01-01Z'),
(6,'Suspended Admin','adminblocked@etl.test','$2a$10$29jzeobe9uBxD3.0TObtO.gaaaBYY9vXeaZcOEb3lZ7Tjn4Q7rbxq',2,'SUSPENDED','2025-01-01Z','2026-01-12Z');
INSERT INTO accounts (account_id,user_id,created_date,updated_date) VALUES
(1,2,'2025-01-01Z','2025-01-01Z'),
(2,2,'2025-01-01Z','2025-01-01Z'),
(3,3,'2025-01-01Z','2025-01-01Z'),
(4,4,'2025-01-01Z','2025-01-01Z'),
(5,5,'2025-01-01Z','2025-01-01Z');
INSERT INTO account_cash_balances (account_id,cash_balance) VALUES
(1,10000),
(2,10000),
(3,10000),
(4,10000),
(5,10000);
INSERT INTO securities (security_id,ticker,name,asset_type,exchange,quote_currency,base_currency,sector) VALUES
(1,'APL','Apple Test','EQUITY','NASDAQ','USD',NULL,'Technology'),
(2,'JPM','Bank Test','EQUITY','NYSE','USD',NULL,'Finance'),
(3,'SPY','ETF Test','ETF','ARCA','USD',NULL,NULL),
(4,'EURUSD','Euro Dollar','FOREX','FX','USD','EUR',NULL),
(5,'BTC','Bitcoin Test','CRYPTO','CRYPTO','USD',NULL,NULL),
(6,'APL','Apple Other Exchange','EQUITY','NYSE','USD',NULL,'Technology');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,order_status,created_date,updated_date,accepted_at,terminal_at,next_attempt_at) VALUES
(1,1,1,'00000000-0000-0000-0000-000000000001','B',100,NULL,'FILLED','2026-01-08T23:59:59+00:00','2026-01-09T23:59:59Z','2026-01-08T23:59:59+00:00','2026-01-09T23:59:59Z',NULL);
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution) VALUES
(1,1,1,1,'B','FILLED','2026-01-09T23:59:59Z','2026-01-09T23:59:59Z',10,'USD','2026-01-09T23:59:59Z','test:etl',1,10.0,10);
INSERT INTO trades (trade_id,execution_id,account_id,trade_date,settlement_date) VALUES
(1,1,1,'2026-01-09T23:59:59Z','2026-01-09T23:59:59Z');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,order_status,created_date,updated_date,accepted_at,terminal_at,next_attempt_at) VALUES
(2,1,1,'00000000-0000-0000-0000-000000000002','S',NULL,4.0,'FILLED','2026-01-09T00:00:00+00:00','2026-01-10T00:00:00Z','2026-01-09T00:00:00+00:00','2026-01-10T00:00:00Z',NULL);
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution) VALUES
(2,2,1,1,'S','FILLED','2026-01-10T00:00:00Z','2026-01-10T00:00:00Z',10,'USD','2026-01-10T00:00:00Z','test:etl',1,4.0,10);
INSERT INTO trades (trade_id,execution_id,account_id,trade_date,settlement_date) VALUES
(2,2,1,'2026-01-10T00:00:00Z','2026-01-10T00:00:00Z');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,order_status,created_date,updated_date,accepted_at,terminal_at,next_attempt_at) VALUES
(3,3,2,'00000000-0000-0000-0000-000000000003','B',70,NULL,'FILLED','2026-01-09T12:00:00+00:00','2026-01-10T12:00:00Z','2026-01-09T12:00:00+00:00','2026-01-10T12:00:00Z',NULL);
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution) VALUES
(3,3,3,2,'B','FILLED','2026-01-10T12:00:00Z','2026-01-10T12:00:00Z',10,'USD','2026-01-10T12:00:00Z','test:etl',1,7.0,10);
INSERT INTO trades (trade_id,execution_id,account_id,trade_date,settlement_date) VALUES
(3,3,3,'2026-01-10T12:00:00Z','2026-01-10T12:00:00Z');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,order_status,created_date,updated_date,accepted_at,terminal_at,next_attempt_at) VALUES
(4,2,3,'00000000-0000-0000-0000-000000000004','B',30,NULL,'FILLED','2026-01-09T23:59:59.999000+00:00','2026-01-10T23:59:59.999Z','2026-01-09T23:59:59.999000+00:00','2026-01-10T23:59:59.999Z',NULL);
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution) VALUES
(4,4,2,3,'B','FILLED','2026-01-10T23:59:59.999Z','2026-01-10T23:59:59.999Z',10,'USD','2026-01-10T23:59:59.999Z','test:etl',1,3.0,10);
INSERT INTO trades (trade_id,execution_id,account_id,trade_date,settlement_date) VALUES
(4,4,2,'2026-01-10T23:59:59.999Z','2026-01-10T23:59:59.999Z');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,order_status,created_date,updated_date,accepted_at,terminal_at,next_attempt_at) VALUES
(5,4,4,'00000000-0000-0000-0000-000000000005','B',50,NULL,'FILLED','2026-01-10T00:00:00+00:00','2026-01-11T00:00:00Z','2026-01-10T00:00:00+00:00','2026-01-11T00:00:00Z',NULL);
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution) VALUES
(5,5,4,4,'B','FILLED','2026-01-11T00:00:00Z','2026-01-11T00:00:00Z',10,'USD','2026-01-11T00:00:00Z','test:etl',1,5.0,10);
INSERT INTO trades (trade_id,execution_id,account_id,trade_date,settlement_date) VALUES
(5,5,4,'2026-01-11T00:00:00Z','2026-01-11T00:00:00Z');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,order_status,created_date,updated_date,accepted_at,terminal_at,next_attempt_at) VALUES
(6,3,5,'00000000-0000-0000-0000-000000000006','S',NULL,6.0,'FILLED','2026-01-10T12:00:00+00:00','2026-01-11T12:00:00Z','2026-01-10T12:00:00+00:00','2026-01-11T12:00:00Z',NULL);
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution) VALUES
(6,6,3,5,'S','FILLED','2026-01-11T12:00:00Z','2026-01-11T12:00:00Z',10,'USD','2026-01-11T12:00:00Z','test:etl',1,6.0,10);
INSERT INTO trades (trade_id,execution_id,account_id,trade_date,settlement_date) VALUES
(6,6,3,'2026-01-11T12:00:00Z','2026-01-11T12:00:00Z');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,order_status,created_date,updated_date,accepted_at,terminal_at,next_attempt_at) VALUES
(7,3,6,'00000000-0000-0000-0000-000000000007','B',20,NULL,'FILLED','2026-01-09T14:00:00+00:00','2026-01-10T14:00:00Z','2026-01-09T14:00:00+00:00','2026-01-10T14:00:00Z',NULL);
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution) VALUES
(7,7,3,6,'B','FILLED','2026-01-10T14:00:00Z','2026-01-10T14:00:00Z',10,'USD','2026-01-10T14:00:00Z','test:etl',1,2.0,10);
INSERT INTO trades (trade_id,execution_id,account_id,trade_date,settlement_date) VALUES
(7,7,3,'2026-01-10T14:00:00Z','2026-01-10T14:00:00Z');
INSERT INTO executions (execution_id,order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,failure_reason) VALUES
(8,3,3,2,'B','FAILED','2026-01-10T10:00:00Z','2026-01-10T10:00:01Z','Test timeout before successful retry');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,order_status,created_date,updated_date,accepted_at,terminal_at,rejection_reason,execution_token,execution_lease_until) VALUES
(8,1,1,'00000000-0000-0000-0000-000000000008','B',100,'SUBMITTED','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z',NULL,NULL,NULL,NULL,NULL);
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,order_status,created_date,updated_date,accepted_at,terminal_at,rejection_reason,execution_token,execution_lease_until) VALUES
(9,1,1,'00000000-0000-0000-0000-000000000009','B',100,'ACCEPTED','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z',NULL,NULL,NULL,NULL);
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,order_status,created_date,updated_date,accepted_at,terminal_at,rejection_reason,execution_token,execution_lease_until) VALUES
(10,1,1,'00000000-0000-0000-0000-00000000000a','B',100,'IN_EXECUTION','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z',NULL,NULL,'00000000-0000-0000-0000-00000000006e','2026-01-10T08:03:00Z');
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,order_status,created_date,updated_date,accepted_at,terminal_at,rejection_reason,execution_token,execution_lease_until) VALUES
(11,1,1,'00000000-0000-0000-0000-00000000000b','B',100,'REJECTED','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z',NULL,'2026-01-10T08:00:00Z','Insufficient cash',NULL,NULL);
INSERT INTO orders (order_id,account_id,security_id,client_request_id,side,requested_amount,order_status,created_date,updated_date,accepted_at,terminal_at,rejection_reason,execution_token,execution_lease_until) VALUES
(12,1,1,'00000000-0000-0000-0000-00000000000c','B',100,'CANCELLED','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z','2026-01-10T08:00:00Z',NULL,NULL,NULL);
INSERT INTO transactions (transaction_id,account_id,client_request_id,transaction_amount,transaction_type,transaction_date,transaction_status,completed_at,failure_reason) VALUES
(1,1,'00000000-0000-0000-0000-0000000000c9',100,'DEPOSIT','2026-01-10T09:00:00Z','COMPLETED','2026-01-10T09:00:00Z',NULL),
(2,3,'00000000-0000-0000-0000-0000000000ca',100,'WITHDRAWAL','2026-01-10T10:00:00Z','FAILED','2026-01-10T10:00:00Z','Insufficient cash'),
(3,3,'00000000-0000-0000-0000-0000000000cb',100,'DEPOSIT','2026-01-10T10:00:00Z','PENDING',NULL,NULL);
INSERT INTO disputes (dispute_id,account_id,admin_id,transaction_id,trade_id,dispute_type,status,date_created,date_resolved) VALUES
(1,1,NULL,1,NULL,'Incorrect deposit','OPEN','2026-01-11T12:00:00Z',NULL),
(2,3,1,NULL,3,'Execution price','RESOLVED','2026-01-11T12:00:00Z','2026-01-12T12:00:00Z');
COMMIT;
