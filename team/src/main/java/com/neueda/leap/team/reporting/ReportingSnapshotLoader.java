package com.neueda.leap.team.reporting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Copies one already-open OLTP snapshot into one already-open OLAP transaction.
 * Facts stream through a server cursor into bounded JDBC batches; only the small
 * currency lookup is kept in memory. No source INSERT/UPDATE/DELETE is executed.
 */
final class ReportingSnapshotLoader {
    private final Connection source;
    private final Connection target;
    private final int batchSize;
    private final int timeoutSeconds;
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final Map<String, Long> currencyKeys = new LinkedHashMap<>();

    ReportingSnapshotLoader(Connection source, Connection target, int batchSize, int timeoutSeconds) {
        this.source = source;
        this.target = target;
        this.batchSize = batchSize;
        this.timeoutSeconds = timeoutSeconds;
    }

    Map<String, Long> replace() throws SQLException {
        // DELETE preserves MVCC visibility for a dashboard reading the previous
        // snapshot. TRUNCATE is deliberately avoided, including for dimensions.
        for (String table : List.of("fact_disputes", "fact_trades", "fact_executions",
                "fact_orders", "fact_transactions", "dim_security", "dim_account",
                "dim_user", "dim_execution_status", "dim_dispute_status",
                "dim_transaction_type", "dim_order_status", "dim_currency", "dim_date")) {
            try (PreparedStatement statement = target.prepareStatement("DELETE FROM public." + table)) {
                statement.setQueryTimeout(timeoutSeconds);
                statement.executeUpdate();
            }
        }
        loadReferenceDimensions();
        loadDates();
        loadCurrencies();
        loadUsersAndAccounts();
        loadSecurities();
        loadOrders();
        loadExecutions();
        loadTrades();
        loadTransactions();
        loadDisputes();
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    private void loadReferenceDimensions() throws SQLException {
        rows("dim_order_status", "order_status_key, order_status_code, order_status_name, is_terminal, is_active", List.of(
                new Object[]{1, "SUBMITTED", "Submitted", false, true},
                new Object[]{2, "ACCEPTED", "Accepted", false, true},
                new Object[]{3, "IN_EXECUTION", "In execution", false, true},
                new Object[]{4, "FILLED", "Filled", true, false},
                new Object[]{5, "REJECTED", "Rejected", true, false},
                new Object[]{6, "CANCELLED", "Cancelled", true, false}));
        rows("dim_execution_status", "execution_status_key, execution_status_code, execution_status_name, is_terminal, is_successful", List.of(
                new Object[]{1, "FILLED", "Filled", true, true},
                new Object[]{2, "REJECTED", "Rejected", true, false},
                new Object[]{3, "FAILED", "Failed", true, false}));
        rows("dim_dispute_status", "dispute_status_key, dispute_status_code, dispute_status_name, is_terminal, is_escalated", List.of(
                new Object[]{1, "OPEN", "Open", false, false},
                new Object[]{2, "UNDER_REVIEW", "Under review", false, false},
                new Object[]{3, "ESCALATED", "Escalated", false, true},
                new Object[]{4, "RESOLVED", "Resolved", true, false},
                new Object[]{5, "REJECTED", "Rejected", true, false}));
        rows("dim_transaction_type", "transaction_type_key, transaction_type_code, transaction_type_name, is_cash_inflow, is_cash_outflow, category", List.of(
                new Object[]{1, "DEPOSIT", "Deposit", true, false, "FUNDING"},
                new Object[]{2, "WITHDRAWAL", "Withdrawal", false, true, "FUNDING"},
                new Object[]{3, "DIVIDEND", "Dividend", true, false, "INCOME"},
                new Object[]{4, "INTEREST", "Interest", true, false, "INCOME"},
                new Object[]{5, "FEE", "Fee", false, true, "EXPENSE"}));
    }

    private void loadDates() throws SQLException {
        copy("dim_date", "date_key, full_date, day_number, day_name, week_number, month_number, month_name, quarter_number, year_number, is_weekend", """
                SELECT DISTINCT (moment AT TIME ZONE 'UTC')::date AS full_date
                FROM (
                    SELECT created_date AS moment FROM public.users
                    UNION ALL SELECT updated_date FROM public.users
                    UNION ALL SELECT created_date FROM public.accounts
                    UNION ALL SELECT updated_date FROM public.accounts
                    UNION ALL SELECT created_date FROM public.orders
                    UNION ALL SELECT updated_date FROM public.orders
                    UNION ALL SELECT accepted_at FROM public.orders
                    UNION ALL SELECT terminal_at FROM public.orders
                    UNION ALL SELECT started_at FROM public.executions
                    UNION ALL SELECT finished_at FROM public.executions
                    UNION ALL SELECT trade_date FROM public.trades
                    UNION ALL SELECT settlement_date FROM public.trades
                    UNION ALL SELECT transaction_date FROM public.transactions
                    UNION ALL SELECT completed_at FROM public.transactions
                    UNION ALL SELECT date_created FROM public.disputes
                    UNION ALL SELECT date_resolved FROM public.disputes
                ) dates WHERE moment IS NOT NULL
                ORDER BY full_date
                """, result -> {
            LocalDate date = result.getObject(1, LocalDate.class);
            return new Object[]{dateKey(date), date, date.getDayOfMonth(),
                    date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    date.get(WeekFields.ISO.weekOfWeekBasedYear()), date.getMonthValue(),
                    date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    (date.getMonthValue() - 1) / 3 + 1, date.getYear(), date.getDayOfWeek().getValue() >= 6};
        });
    }

    private void loadCurrencies() throws SQLException {
        copy("dim_currency", "currency_key, currency_code, currency_name, country, is_base_currency", """
                SELECT currency FROM (
                    SELECT 'USD'::text AS currency
                    UNION SELECT currency::text FROM public.accounts
                    UNION SELECT quote_currency::text FROM public.securities
                    UNION SELECT base_currency::text FROM public.securities
                    UNION SELECT quote_currency::text FROM public.executions
                ) codes WHERE currency IS NOT NULL ORDER BY currency
                """, result -> {
            String code = result.getString(1).trim();
            long key = currencyKeys.size() + 1L;
            currencyKeys.put(code, key);
            // An ISO-looking code is allowed by OLTP even if Java's currency
            // catalogue lacks it; the code itself is an honest display label.
            return new Object[]{key, code, code, null, "USD".equals(code)};
        });
    }

    private void loadUsersAndAccounts() throws SQLException {
        copy("dim_user", "user_key, user_id, user_name, role_name, status, is_blacklisted, created_at, updated_at", """
                SELECT u.user_id, u.user_id, u.name, r.name, u.status,
                       (u.status = 'SUSPENDED'), u.created_date, u.updated_date
                FROM public.users u JOIN public.roles r ON r.role_id = u.role_id
                ORDER BY u.user_id
                """, ReportingSnapshotLoader::values);
        copy("dim_account", "account_key, account_id, user_key, currency_key, created_date, created_at, updated_at", """
                SELECT account_id, account_id, user_id, currency,
                       (created_date AT TIME ZONE 'UTC')::date, created_date, updated_date
                FROM public.accounts ORDER BY account_id
                """, result -> withCurrency(values(result), 3));
    }

    private void loadSecurities() throws SQLException {
        copy("dim_security", "security_key, security_id, ticker, company_name, sector, asset_type, exchange, security_status, quote_currency_key, base_currency_key", """
                SELECT security_id, security_id, ticker, name, sector, asset_type,
                       exchange, status, quote_currency, base_currency
                FROM public.securities ORDER BY security_id
                """, result -> withCurrency(withCurrency(values(result), 8), 9));
    }

    private void loadOrders() throws SQLException {
        copy("fact_orders", "order_fact_key, order_id, submission_date_key, user_key, account_key, security_key, order_status_key, side, quantity_ordered, requested_amount, accepted_at_date_key, terminal_at_date_key, submitted_at, updated_at, accepted_at, terminal_at, rejection_reason", """
                SELECT o.order_id, o.order_id,
                       to_char(o.created_date AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       a.user_id, o.account_id, o.security_id,
                       CASE o.order_status WHEN 'SUBMITTED' THEN 1 WHEN 'ACCEPTED' THEN 2
                         WHEN 'IN_EXECUTION' THEN 3 WHEN 'FILLED' THEN 4
                         WHEN 'REJECTED' THEN 5 WHEN 'CANCELLED' THEN 6 END,
                       o.side, o.quantity_ordered, o.requested_amount,
                       to_char(o.accepted_at AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       to_char(o.terminal_at AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       o.created_date, o.updated_date, o.accepted_at, o.terminal_at, o.rejection_reason
                FROM public.orders o JOIN public.accounts a ON a.account_id = o.account_id
                ORDER BY o.order_id
                """, ReportingSnapshotLoader::values);
    }

    private void loadExecutions() throws SQLException {
        copy("fact_executions", "execution_fact_key, execution_id, order_id, started_at_date_key, finished_at_date_key, user_key, account_key, security_key, execution_status_key, side, quantity_filled, price_of_execution, cash_amount, quote_price, quote_currency_key, fx_rate_to_usd, started_at, finished_at, failure_reason, exchange_trade_id", """
                SELECT e.execution_id, e.execution_id, e.order_id,
                       to_char(e.started_at AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       to_char(e.finished_at AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       a.user_id, e.account_id, e.security_id,
                       CASE e.status_of_execution WHEN 'FILLED' THEN 1
                         WHEN 'REJECTED' THEN 2 WHEN 'FAILED' THEN 3 END,
                       e.side, e.quantity_filled, e.price_of_execution, e.cash_amount,
                       e.quote_price, e.quote_currency, e.fx_rate_to_usd,
                       e.started_at, e.finished_at, e.failure_reason, e.exchange_trade_id
                FROM public.executions e JOIN public.accounts a ON a.account_id = e.account_id
                ORDER BY e.execution_id
                """, result -> withCurrency(values(result), 14));
    }

    private void loadTrades() throws SQLException {
        copy("fact_trades", "trade_fact_key, trade_id, execution_id, order_id, trade_date_key, settlement_date_key, user_key, account_key, security_key, trade_type, shares, trade_price, trade_value, traded_at, settled_at", """
                SELECT t.trade_id, t.trade_id, t.execution_id, e.order_id,
                       to_char(t.trade_date AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       to_char(t.settlement_date AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       a.user_id, t.account_id, e.security_id, e.side,
                       e.quantity_filled, e.price_of_execution, e.cash_amount,
                       t.trade_date, t.settlement_date
                FROM public.trades t JOIN public.executions e ON e.execution_id = t.execution_id
                JOIN public.accounts a ON a.account_id = t.account_id
                ORDER BY t.trade_id
                """, ReportingSnapshotLoader::values);
    }

    private void loadTransactions() throws SQLException {
        copy("fact_transactions", "transaction_fact_key, transaction_id, transaction_date_key, completed_at_date_key, user_key, account_key, transaction_type_key, transaction_amount, transaction_status, transaction_at, completed_at, failure_reason", """
                SELECT t.transaction_id, t.transaction_id,
                       to_char(t.transaction_date AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       to_char(t.completed_at AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       a.user_id, t.account_id,
                       CASE t.transaction_type WHEN 'DEPOSIT' THEN 1 WHEN 'WITHDRAWAL' THEN 2
                         WHEN 'DIVIDEND' THEN 3 WHEN 'INTEREST' THEN 4 WHEN 'FEE' THEN 5 END,
                       t.transaction_amount, t.transaction_status, t.transaction_date,
                       t.completed_at, t.failure_reason
                FROM public.transactions t JOIN public.accounts a ON a.account_id = t.account_id
                ORDER BY t.transaction_id
                """, ReportingSnapshotLoader::values);
    }

    private void loadDisputes() throws SQLException {
        copy("fact_disputes", "dispute_fact_key, dispute_id, created_date_key, resolved_date_key, user_key, admin_key, account_key, dispute_status_key, transaction_id, trade_id, is_transaction_dispute, dispute_type, description, created_at, resolved_at", """
                SELECT d.dispute_id, d.dispute_id,
                       to_char(d.date_created AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       to_char(d.date_resolved AT TIME ZONE 'UTC', 'YYYYMMDD')::integer,
                       a.user_id, d.admin_id, d.account_id,
                       CASE d.status WHEN 'OPEN' THEN 1 WHEN 'UNDER_REVIEW' THEN 2
                         WHEN 'ESCALATED' THEN 3 WHEN 'RESOLVED' THEN 4 WHEN 'REJECTED' THEN 5 END,
                       d.transaction_id, d.trade_id, (d.transaction_id IS NOT NULL),
                       d.dispute_type, d.description, d.date_created, d.date_resolved
                FROM public.disputes d JOIN public.accounts a ON a.account_id = d.account_id
                ORDER BY d.dispute_id
                """, ReportingSnapshotLoader::values);
    }

    private void copy(String table, String columns, String select, RowValues mapper) throws SQLException {
        int width = columns.split(",").length;
        try (PreparedStatement read = source.prepareStatement(select, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement write = writer(table, columns, width)) {
            read.setFetchSize(batchSize); // PostgreSQL uses a cursor because autocommit is false.
            read.setQueryTimeout(timeoutSeconds);
            long count = 0;
            int pending = 0;
            try (ResultSet result = read.executeQuery()) {
                while (result.next()) {
                    Object[] row = mapper.map(result);
                    if (row.length != width) throw new SQLException("ETL column mapping mismatch: " + table);
                    add(write, row);
                    count++;
                    if (++pending == batchSize) {
                        write.executeBatch();
                        write.clearBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) write.executeBatch();
            counts.put(table, count);
        }
    }

    private void rows(String table, String columns, List<Object[]> rows) throws SQLException {
        try (PreparedStatement write = writer(table, columns, columns.split(",").length)) {
            for (Object[] row : rows) add(write, row);
            write.executeBatch();
        }
        counts.put(table, (long) rows.size());
    }

    private PreparedStatement writer(String table, String columns, int width) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(width, "?"));
        PreparedStatement statement = target.prepareStatement("INSERT INTO public." + table
                + " (" + columns + ") VALUES (" + placeholders + ")");
        statement.setQueryTimeout(timeoutSeconds);
        return statement;
    }

    private static void add(PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
        statement.addBatch();
    }

    private static Object[] values(ResultSet result) throws SQLException {
        Object[] row = new Object[result.getMetaData().getColumnCount()];
        for (int i = 0; i < row.length; i++) row[i] = result.getObject(i + 1);
        return row;
    }

    private Object[] withCurrency(Object[] row, int index) throws SQLException {
        if (row[index] != null) {
            Long key = currencyKeys.get(row[index].toString().trim());
            if (key == null) throw new SQLException("Currency missing from the source snapshot lookup");
            row[index] = key;
        }
        return row;
    }

    static int dateKey(LocalDate date) {
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    @FunctionalInterface
    private interface RowValues { Object[] map(ResultSet result) throws SQLException; }
}
