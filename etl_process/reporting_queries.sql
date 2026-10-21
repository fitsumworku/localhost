-- Run in the OLAP database after a successful ETL refresh.
-- These examples use the same definitions as the admin reporting API.
-- Edit the two bounds for a period, e.g. 2026-10-01T00:00:00Z through
-- 2026-11-01T00:00:00Z. Intervals include the start and exclude the end.
-- '-infinity'/'infinity' select all history. UTC prevents local-date surprises.
-- A repeatable-read transaction keeps all panels on one committed ETL snapshot.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL TIME ZONE 'UTC';
SET LOCAL paysprint.report_from = '-infinity';
SET LOCAL paysprint.report_to = 'infinity';

-- Freshness: NULL last_successful_at means no ETL has completed yet.
SELECT source_snapshot_at, last_successful_at, duration_ms, refresh_count, row_counts
FROM etl_metadata WHERE metadata_id = 1;

-- Total trading volume = gross USD notional of successful trades, buy + sell.
-- No requested-but-unfilled orders, failed attempts, deposits or withdrawals.
-- Unit quantities are NOT summed across unrelated instruments.
SELECT count(*) AS trade_count,
       coalesce(sum(trade_value), 0) AS trading_volume_usd,
       coalesce(sum(trade_value) FILTER (WHERE trade_type = 'B'), 0) AS buy_volume_usd,
       coalesce(sum(trade_value) FILTER (WHERE trade_type = 'S'), 0) AS sell_volume_usd,
       count(*) FILTER (WHERE trade_type = 'B') AS buy_trade_count,
       count(*) FILTER (WHERE trade_type = 'S') AS sell_trade_count
FROM fact_trades
WHERE traded_at >= current_setting('paysprint.report_from')::timestamptz
  AND traded_at < current_setting('paysprint.report_to')::timestamptz;

-- Sector is the current security sector. Include every asset type; null/blank
-- sectors share an explicit group rather than disappearing from the totals.
SELECT coalesce(nullif(btrim(s.sector), ''), 'Unclassified') AS sector,
       count(*) AS trade_count,
       sum(t.trade_value) AS trading_volume_usd
FROM fact_trades t
JOIN dim_security s ON s.security_key = t.security_key
WHERE t.traded_at >= current_setting('paysprint.report_from')::timestamptz
  AND t.traded_at < current_setting('paysprint.report_to')::timestamptz
GROUP BY coalesce(nullif(btrim(s.sector), ''), 'Unclassified')
ORDER BY trading_volume_usd DESC, sector;

-- By instrument: group by security ID and exchange, since a ticker alone need
-- not identify a security. Add AND s.asset_type = 'EQUITY' for stocks only.
SELECT s.security_id, s.ticker, s.exchange, s.company_name, s.asset_type,
       count(*) AS trade_count,
       sum(t.shares) AS units_traded,
       sum(t.trade_value) AS trading_volume_usd,
       coalesce(sum(t.trade_value) FILTER (WHERE t.trade_type = 'B'), 0) AS buy_volume_usd,
       coalesce(sum(t.trade_value) FILTER (WHERE t.trade_type = 'S'), 0) AS sell_volume_usd
FROM fact_trades t
JOIN dim_security s ON s.security_key = t.security_key
WHERE t.traded_at >= current_setting('paysprint.report_from')::timestamptz
  AND t.traded_at < current_setting('paysprint.report_to')::timestamptz
GROUP BY s.security_id, s.ticker, s.exchange, s.company_name, s.asset_type
ORDER BY trading_volume_usd DESC, s.security_id;

-- Top trading clients, across ALL accounts belonging to each user.
-- A current suspension does not erase the client's historical trades.
SELECT u.user_id, u.user_name, u.status,
       count(*) AS trade_count,
       sum(t.trade_value) AS trading_volume_usd
FROM fact_trades t
JOIN dim_user u ON u.user_key = t.user_key
WHERE u.role_name = 'CLIENT'
  AND t.traded_at >= current_setting('paysprint.report_from')::timestamptz
  AND t.traded_at < current_setting('paysprint.report_to')::timestamptz
GROUP BY u.user_id, u.user_name, u.status
ORDER BY trading_volume_usd DESC, u.user_id
LIMIT 10;

-- Current registered clients, as of the ETL snapshot (not a historical cohort).
-- Counts users directly, so multiple accounts do not inflate them. Admin logins
-- are excluded. SUSPENDED is the project's blacklist status.
SELECT count(*) AS total_clients,
       count(*) FILTER (WHERE status = 'ACTIVE') AS active_clients,
       count(*) FILTER (WHERE status = 'SUSPENDED') AS blacklisted_clients
FROM dim_user
WHERE role_name = 'CLIENT';

-- A second, distinct definition of activity: clients with >=1 successful trade
-- in the selected interval. This is not login activity; OLTP stores no login history.
SELECT count(DISTINCT t.user_key) AS trading_active_clients
FROM fact_trades t
JOIN dim_user u ON u.user_key = t.user_key
WHERE u.role_name = 'CLIENT'
  AND t.traded_at >= current_setting('paysprint.report_from')::timestamptz
  AND t.traded_at < current_setting('paysprint.report_to')::timestamptz;

-- Orders submitted IN the interval and their CURRENT status. All six statuses
-- appear even when zero. The cohort total is all submitted orders, not just rows
-- whose status is still SUBMITTED. Filled count is not the count of fills by date.
SELECT s.order_status_code, count(o.order_id) AS order_count
FROM dim_order_status s
LEFT JOIN fact_orders o ON o.order_status_key = s.order_status_key
  AND o.submitted_at >= current_setting('paysprint.report_from')::timestamptz
  AND o.submitted_at < current_setting('paysprint.report_to')::timestamptz
GROUP BY s.order_status_key, s.order_status_code
ORDER BY s.order_status_key;

SELECT count(*) AS orders_submitted,
       count(*) FILTER (WHERE s.order_status_code = 'FILLED') AS orders_filled,
       count(*) FILTER (WHERE s.order_status_code = 'REJECTED') AS orders_rejected,
       count(*) FILTER (WHERE s.order_status_code = 'CANCELLED') AS orders_cancelled,
       count(*) FILTER (WHERE s.is_active) AS orders_active,
       coalesce(round(100.0 * count(*) FILTER (WHERE s.order_status_code = 'FILLED')
           / nullif(count(*), 0), 2), 0) AS fill_rate_percent
FROM fact_orders o
JOIN dim_order_status s ON s.order_status_key = o.order_status_key
WHERE o.submitted_at >= current_setting('paysprint.report_from')::timestamptz
  AND o.submitted_at < current_setting('paysprint.report_to')::timestamptz;

COMMIT;
