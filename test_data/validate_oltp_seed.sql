-- Read-only validation of a full-history test dataset for initdb/oltp_schema.sql.
-- Run after loading oltp_seed.sql, with the application/order worker stopped.
-- Every returned violations value must be zero. Foreign keys and CHECK constraints
-- already validate individual row shapes; these checks validate cross-row behavior.
-- Assumptions: balances/holdings start at zero; no unrecorded opening balances,
-- corporate actions, or reversal adjustments. Events at the same timestamp are
-- treated as one atomic batch for cash/availability history. Position replay uses
-- (trade_date, trade_id) order and matches OrderExecutionService's rounding policy.
-- Optional ledger/dispute/audit rows are checked if present, but not required.
WITH RECURSIVE
cash_events AS (
    SELECT account_id, completed_at AS event_at,
           CASE WHEN transaction_type IN ('DEPOSIT','DIVIDEND','INTEREST')
                THEN transaction_amount ELSE -transaction_amount END AS delta
    FROM transactions WHERE transaction_status = 'COMPLETED'
    UNION ALL
    SELECT t.account_id, t.trade_date,
           CASE WHEN e.side = 'S' THEN e.cash_amount ELSE -e.cash_amount END
    FROM trades t JOIN executions e USING (execution_id)
    WHERE e.status_of_execution = 'FILLED'
),
cash_batches AS (
    SELECT account_id, event_at, sum(delta) AS delta
    FROM cash_events GROUP BY account_id, event_at
),
cash_history AS (
    SELECT account_id, event_at,
           sum(delta) OVER (PARTITION BY account_id ORDER BY event_at
                           ROWS UNBOUNDED PRECEDING) AS balance
    FROM cash_batches
),
cash_totals AS (
    SELECT account_id, sum(delta) AS balance FROM cash_events GROUP BY account_id
),
position_events AS (
    SELECT t.account_id, e.security_id, t.trade_id, t.trade_date,
           e.side, e.quantity_filled, e.cash_amount,
           CASE WHEN e.side = 'B' THEN e.quantity_filled
                ELSE -e.quantity_filled END AS delta
    FROM trades t JOIN executions e USING (execution_id)
    WHERE e.status_of_execution = 'FILLED'
),
position_numbered AS (
    SELECT *, row_number() OVER (
        PARTITION BY account_id, security_id ORDER BY trade_date, trade_id
    ) AS rn FROM position_events
),
position_replay(account_id, security_id, rn, quantity, average_price) AS (
    SELECT account_id, security_id, 0::bigint, 0::numeric, 0::numeric
    FROM position_events GROUP BY account_id, security_id
    UNION ALL
    SELECT p.account_id, p.security_id, e.rn,
           p.quantity + e.delta,
           CASE WHEN p.quantity + e.delta = 0 THEN 0::numeric
                WHEN e.side = 'B' THEN
                    round((p.quantity * p.average_price + e.cash_amount)
                          / NULLIF(p.quantity + e.delta, 0), 12)
                ELSE p.average_price END
    FROM position_replay p JOIN position_numbered e
      ON e.account_id = p.account_id AND e.security_id = p.security_id
     AND e.rn = p.rn + 1
),
position_final AS (
    SELECT DISTINCT ON (account_id, security_id)
           account_id, security_id, quantity, average_price
    FROM position_replay ORDER BY account_id, security_id, rn DESC
),
cash_availability_events AS (
    SELECT account_id, event_at, delta FROM cash_events
    UNION ALL
    SELECT account_id, created_date, -reserved_cash
    FROM order_reservations WHERE side = 'B'
    UNION ALL
    SELECT account_id, resolved_at, reserved_cash
    FROM order_reservations WHERE side = 'B' AND resolved_at IS NOT NULL
),
cash_availability_batches AS (
    SELECT account_id, event_at, sum(delta) AS delta
    FROM cash_availability_events GROUP BY account_id, event_at
),
cash_availability_history AS (
    SELECT account_id, event_at,
           sum(delta) OVER (PARTITION BY account_id ORDER BY event_at
                           ROWS UNBOUNDED PRECEDING) AS available
    FROM cash_availability_batches
),
position_availability_events AS (
    SELECT account_id, security_id, trade_date AS event_at, delta
    FROM position_events
    UNION ALL
    SELECT account_id, security_id, created_date, -reserved_quantity
    FROM order_reservations WHERE side = 'S'
    UNION ALL
    SELECT account_id, security_id, resolved_at, reserved_quantity
    FROM order_reservations WHERE side = 'S' AND resolved_at IS NOT NULL
),
position_availability_batches AS (
    SELECT account_id, security_id, event_at, sum(delta) AS delta
    FROM position_availability_events GROUP BY account_id, security_id, event_at
),
position_availability_history AS (
    SELECT account_id, security_id, event_at,
           sum(delta) OVER (PARTITION BY account_id, security_id ORDER BY event_at
                           ROWS UNBOUNDED PRECEDING) AS available
    FROM position_availability_batches
),
execution_counts AS (
    SELECT order_id, count(*) AS all_count,
           count(*) FILTER (WHERE status_of_execution = 'FILLED') AS filled_count,
           count(*) FILTER (WHERE status_of_execution = 'REJECTED') AS rejected_count
    FROM executions GROUP BY order_id
),
checks AS (
    SELECT 'accounts_have_client_owners' AS check_name, count(*) AS violations
    FROM accounts a JOIN users u USING (user_id) JOIN roles r USING (role_id)
    WHERE r.name <> 'CLIENT'
    UNION ALL
    SELECT 'accounts_have_cash_rows', count(*)
    FROM accounts a LEFT JOIN account_cash_balances b USING (account_id)
    WHERE b.account_id IS NULL
    UNION ALL
    SELECT 'cash_balance_matches_complete_history', count(*)
    FROM account_cash_balances b LEFT JOIN cash_totals c USING (account_id)
    WHERE b.cash_balance <> COALESCE(c.balance, 0)
    UNION ALL
    SELECT 'cash_never_negative_through_history', count(*)
    FROM cash_history WHERE balance < 0
    UNION ALL
    SELECT 'available_cash_never_negative_through_history', count(*)
    FROM cash_availability_history WHERE available < 0
    UNION ALL
    SELECT 'available_cash_currently_nonnegative', count(*)
    FROM account_cash_availability WHERE available_cash < 0
    UNION ALL
    SELECT 'positions_match_trading_history', count(*)
    FROM account_positions p FULL JOIN position_final f USING (account_id, security_id)
    WHERE p.account_id IS NULL
       OR p.quantity <> COALESCE(f.quantity, 0)
       OR p.average_price <> COALESCE(f.average_price, 0)
    UNION ALL
    SELECT 'position_quantity_never_negative_through_history', count(*)
    FROM position_replay WHERE quantity < 0
    UNION ALL
    SELECT 'available_quantity_never_negative_through_history', count(*)
    FROM position_availability_history WHERE available < 0
    UNION ALL
    SELECT 'active_sell_holds_have_enough_position', count(*)
    FROM (
        SELECT account_id, security_id, sum(reserved_quantity) AS held
        FROM order_reservations WHERE side = 'S' AND status = 'ACTIVE'
        GROUP BY account_id, security_id
    ) h LEFT JOIN account_positions p USING (account_id, security_id)
    WHERE h.held > COALESCE(p.quantity, 0)
    UNION ALL
    SELECT 'filled_orders_have_one_filled_execution', count(*)
    FROM orders o LEFT JOIN execution_counts e USING (order_id)
    WHERE (o.order_status = 'FILLED' AND COALESCE(e.filled_count, 0) <> 1)
       OR (o.order_status <> 'FILLED' AND COALESCE(e.filled_count, 0) <> 0)
    UNION ALL
    SELECT 'filled_executions_have_one_trade', count(*)
    FROM executions e LEFT JOIN trades t USING (execution_id)
    WHERE e.status_of_execution = 'FILLED' AND t.trade_id IS NULL
    UNION ALL
    SELECT 'trades_only_reference_filled_executions', count(*)
    FROM trades t JOIN executions e USING (execution_id)
    WHERE e.status_of_execution <> 'FILLED'
    UNION ALL
    SELECT 'reservation_lifecycle_matches_order', count(*)
    FROM orders o LEFT JOIN order_reservations r USING (order_id)
    WHERE (o.order_status IN ('ACCEPTED','IN_EXECUTION')
           AND r.status IS DISTINCT FROM 'ACTIVE')
       OR (o.order_status = 'FILLED' AND r.status IS DISTINCT FROM 'CONSUMED')
       OR (o.order_status = 'CANCELLED' AND r.status IS DISTINCT FROM 'RELEASED')
       OR (o.order_status = 'REJECTED' AND o.accepted_at IS NOT NULL
           AND r.status IS DISTINCT FROM 'RELEASED')
       OR (o.accepted_at IS NULL AND r.order_id IS NOT NULL)
    UNION ALL
    SELECT 'reservation_amount_matches_order', count(*)
    FROM orders o JOIN order_reservations r USING (order_id)
    WHERE r.reserved_cash IS DISTINCT FROM o.requested_amount
       OR r.reserved_quantity IS DISTINCT FROM o.quantity_ordered
    UNION ALL
    SELECT 'rejected_after_acceptance_has_rejected_execution', count(*)
    FROM orders o LEFT JOIN execution_counts e USING (order_id)
    WHERE o.order_status = 'REJECTED' AND o.accepted_at IS NOT NULL
      AND COALESCE(e.rejected_count, 0) <> 1
    UNION ALL
    SELECT 'rejected_execution_only_for_rejected_order', count(*)
    FROM executions e JOIN orders o USING (order_id)
    WHERE e.status_of_execution = 'REJECTED' AND o.order_status <> 'REJECTED'
    UNION ALL
    SELECT 'unaccepted_orders_have_no_executions', count(*)
    FROM orders o JOIN executions e USING (order_id) WHERE o.accepted_at IS NULL
    UNION ALL
    SELECT 'attempt_count_covers_completed_executions', count(*)
    FROM orders o JOIN execution_counts e USING (order_id)
    WHERE o.execution_attempts < e.all_count
    UNION ALL
    SELECT 'terminal_orders_not_queued', count(*)
    FROM orders WHERE order_status IN ('FILLED','REJECTED','CANCELLED')
      AND next_attempt_at IS NOT NULL
    UNION ALL
    SELECT 'accepted_orders_have_next_attempt', count(*)
    FROM orders WHERE order_status = 'ACCEPTED' AND next_attempt_at IS NULL
    UNION ALL
    SELECT 'buy_fill_matches_dollar_budget_rounding', count(*)
    FROM orders o JOIN executions e USING (order_id)
    WHERE e.status_of_execution = 'FILLED' AND o.side = 'B'
      AND (e.quantity_filled <> trunc(o.requested_amount / e.price_of_execution, 12)
           OR e.cash_amount > o.requested_amount)
    UNION ALL
    SELECT 'sell_fills_entire_ordered_quantity', count(*)
    FROM orders o JOIN executions e USING (order_id)
    WHERE e.status_of_execution = 'FILLED' AND o.side = 'S'
      AND e.quantity_filled <> o.quantity_ordered
    UNION ALL
    SELECT 'account_created_after_owner', count(*)
    FROM accounts a JOIN users u USING (user_id) WHERE a.created_date < u.created_date
    UNION ALL
    SELECT 'account_children_created_after_account', count(*)
    FROM (
        SELECT account_id, created_date AS event_at FROM orders
        UNION ALL SELECT account_id, transaction_date FROM transactions
    ) x JOIN accounts a USING (account_id) WHERE x.event_at < a.created_date
    UNION ALL
    SELECT 'order_updated_after_state_timestamps', count(*)
    FROM orders WHERE updated_date < accepted_at OR updated_date < terminal_at
    UNION ALL
    SELECT 'reservation_chronology', count(*)
    FROM order_reservations r JOIN orders o USING (order_id)
    WHERE r.created_date < o.accepted_at OR r.created_date > o.updated_date
       OR r.resolved_at > o.updated_date
       OR (o.terminal_at IS NOT NULL AND r.resolved_at <> o.terminal_at)
    UNION ALL
    SELECT 'execution_chronology', count(*)
    FROM executions e JOIN orders o USING (order_id)
    WHERE e.started_at < o.accepted_at OR e.finished_at > o.updated_date
       OR (o.terminal_at IS NOT NULL AND e.finished_at > o.terminal_at)
    UNION ALL
    SELECT 'trade_chronology', count(*)
    FROM trades t JOIN executions e USING (execution_id)
    WHERE t.trade_date < e.finished_at
    UNION ALL
    SELECT 'cash_row_timestamp_covers_cash_history', count(*)
    FROM account_cash_balances b JOIN accounts a USING (account_id)
    WHERE b.updated_date < a.created_date
       OR EXISTS (SELECT 1 FROM cash_events c
                  WHERE c.account_id = b.account_id AND c.event_at > b.updated_date)
    UNION ALL
    SELECT 'position_timestamp_covers_trading_history', count(*)
    FROM account_positions p JOIN accounts a USING (account_id)
    WHERE p.updated_date < a.created_date
       OR EXISTS (SELECT 1 FROM position_events e
                  WHERE e.account_id = p.account_id AND e.security_id = p.security_id
                    AND e.trade_date > p.updated_date)
    UNION ALL
    SELECT 'ledger_transaction_matches_completed_source', count(*)
    FROM cash_ledger l JOIN transactions t USING (transaction_id)
    WHERE t.transaction_status <> 'COMPLETED'
       OR l.entry_type <> t.transaction_type
       OR l.debit_amount + l.credit_amount <> t.transaction_amount
       OR l.entry_date < t.completed_at
    UNION ALL
    SELECT 'ledger_trade_matches_filled_source', count(*)
    FROM cash_ledger l JOIN trades t USING (trade_id)
    JOIN executions e USING (execution_id)
    WHERE e.status_of_execution <> 'FILLED'
       OR l.entry_type <> CASE WHEN e.side = 'B' THEN 'BUY' ELSE 'SELL' END
       OR l.debit_amount + l.credit_amount <> e.cash_amount
       OR l.entry_date < t.trade_date
    UNION ALL
    SELECT 'ledger_balance_matches_economic_history', count(*)
    FROM cash_ledger l
    WHERE l.running_balance <> COALESCE((
        SELECT sum(c.delta) FROM cash_events c
        WHERE c.account_id = l.account_id AND c.event_at <= l.entry_date
    ), 0)
    UNION ALL
    SELECT 'disputes_assigned_only_to_admins', count(*)
    FROM disputes d JOIN users u ON u.user_id = d.admin_id JOIN roles r USING (role_id)
    WHERE r.name <> 'ADMIN'
    UNION ALL
    SELECT 'dispute_created_after_source', count(*)
    FROM disputes d LEFT JOIN transactions t USING (transaction_id)
    LEFT JOIN trades tr USING (trade_id)
    WHERE d.date_created < COALESCE(t.transaction_date, tr.trade_date)
    UNION ALL
    SELECT 'audit_created_after_actor_and_subject', count(*)
    FROM audit_logs a LEFT JOIN users actor ON actor.user_id = a.actor_user_id
    LEFT JOIN users subject ON subject.user_id = a.subject_user_id
    WHERE a.timestamp < actor.created_date OR a.timestamp < subject.created_date
)
SELECT check_name, violations FROM checks ORDER BY check_name;
