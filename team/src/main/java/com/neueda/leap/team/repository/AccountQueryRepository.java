package com.neueda.leap.team.repository;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.enums.*;
import com.neueda.leap.team.exception.ApiException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

/** Read projections over the existing schema. Every account-specific query is scoped by account_id. */
@Repository
public class AccountQueryRepository {
    private final JdbcTemplate jdbc;
    public AccountQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String ACCOUNTS = """
            SELECT a.*, v.cash_balance, v.reserved_cash, v.available_cash, b.updated_date AS balance_updated_date
            FROM accounts a
            LEFT JOIN account_cash_availability v ON v.account_id = a.account_id
            LEFT JOIN account_cash_balances b ON b.account_id = a.account_id
            """;
    private static final String TRANSACTIONS = "SELECT t.* FROM transactions t ";

    public Optional<AccountDto> account(long id) {
        return jdbc.query(ACCOUNTS + " WHERE a.account_id = ?", this::accountRow, id).stream().findFirst();
    }

    public PageResponse<AccountDto> accounts(Long ownerId, int page, int size) {
        validatePage(page, size);
        String where = ownerId == null ? "" : " WHERE a.user_id = ?";
        var params = new ArrayList<Object>();
        if (ownerId != null) params.add(ownerId);
        long count = jdbc.queryForObject("SELECT count(*) FROM accounts a" + where, Long.class, params.toArray());
        params.add(size); params.add((long) page * size);
        var content = jdbc.query(ACCOUNTS + where + " ORDER BY a.created_date DESC, a.account_id DESC LIMIT ? OFFSET ?",
                this::accountRow, params.toArray());
        return PageResponse.of(content, page, size, count);
    }

    public BigDecimal reservedCash(long accountId) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(reserved_cash), 0) FROM order_reservations
                WHERE account_id = ? AND side = 'B' AND status = 'ACTIVE'
                """, BigDecimal.class, accountId);
    }

    public PageResponse<PositionDto> positions(long id, int page, int size) {
        return accountPage("SELECT count(*) FROM account_positions WHERE account_id = ? AND quantity > 0", """
                SELECT p.*, v.reserved_quantity, v.available_quantity, s.ticker, s.name, s.asset_type,
                       s.exchange, s.quote_currency, a.currency AS account_currency
                FROM account_positions p JOIN position_availability v ON v.position_id = p.position_id
                JOIN securities s ON s.security_id = p.security_id JOIN accounts a ON a.account_id = p.account_id
                WHERE p.account_id = ? AND p.quantity > 0 ORDER BY s.ticker, p.position_id LIMIT ? OFFSET ?
                """, (r, row) -> new PositionDto(r.getLong("position_id"), id, r.getLong("security_id"),
                r.getString("ticker"), r.getString("name"), r.getString("asset_type"), r.getString("exchange"),
                r.getString("quote_currency"), r.getString("account_currency"), r.getBigDecimal("quantity"),
                r.getBigDecimal("reserved_quantity"), r.getBigDecimal("available_quantity"),
                r.getBigDecimal("average_price"), instant(r, "updated_date")), id, page, size);
    }

    public Optional<CashTransactionDto> transaction(long accountId, long transactionId) {
        return jdbc.query(TRANSACTIONS + " WHERE t.account_id = ? AND t.transaction_id = ?",
                this::transactionRow, accountId, transactionId).stream().findFirst();
    }

    public PageResponse<CashTransactionDto> transactions(long id, int page, int size) {
        return accountPage("SELECT count(*) FROM transactions WHERE account_id = ?", TRANSACTIONS + """
                WHERE t.account_id = ? ORDER BY t.transaction_date DESC, t.transaction_id DESC LIMIT ? OFFSET ?
                """, this::transactionRow, id, page, size);
    }

    public PageResponse<AccountOrderDto> orders(long id, int page, int size) {
        return accountPage("SELECT count(*) FROM orders WHERE account_id = ?", """
                SELECT o.*, s.ticker, s.name FROM orders o JOIN securities s ON s.security_id = o.security_id
                WHERE o.account_id = ? ORDER BY o.created_date DESC, o.order_id DESC LIMIT ? OFFSET ?
                """, (r, row) -> new AccountOrderDto(r.getLong("order_id"), id, r.getLong("security_id"),
                r.getString("ticker"), r.getString("name"), r.getObject("client_request_id", UUID.class),
                r.getString("side"), r.getBigDecimal("requested_amount"), r.getBigDecimal("quantity_ordered"),
                r.getString("order_status"), instant(r, "created_date"), instant(r, "updated_date"),
                instant(r, "accepted_at"), instant(r, "terminal_at"), r.getString("rejection_reason")), id, page, size);
    }

    public PageResponse<AccountTradeDto> trades(long id, int page, int size) {
        return accountPage("SELECT count(*) FROM trades WHERE account_id = ?", """
                SELECT t.*, s.ticker, s.name FROM trade_details t JOIN securities s ON s.security_id = t.security_id
                WHERE t.account_id = ? ORDER BY t.trade_date DESC, t.trade_id DESC LIMIT ? OFFSET ?
                """, (r, row) -> new AccountTradeDto(r.getLong("trade_id"), id, r.getLong("order_id"),
                r.getLong("execution_id"), r.getLong("security_id"), r.getString("ticker"), r.getString("name"),
                r.getString("side"), r.getBigDecimal("quantity_filled"), r.getBigDecimal("price_of_execution"),
                r.getBigDecimal("cash_amount"), instant(r, "trade_date"), instant(r, "settlement_date"),
                r.getBigDecimal("quote_price"), r.getString("quote_currency"), instant(r, "quote_timestamp"),
                r.getString("quote_source"), r.getBigDecimal("fx_rate_to_usd"), instant(r, "fx_quote_timestamp"),
                r.getString("fx_source")), id, page, size);
    }

    /** A change to cash and/or one holding. Undoing these newest-first rewinds the account to earlier days. */
    public record ValueEvent(Instant at, Long securityId, BigDecimal quantityChange, BigDecimal cashChange) {}

    /** Every instrument held now or ever traded, with what is needed to value it in USD. */
    public record Instrument(long securityId, String ticker, String quoteCurrency, BigDecimal quantity,
            BigDecimal fxRateToUsd, BigDecimal fallbackUsdPrice) {}

    public List<ValueEvent> valueEventsSince(long id, Instant since) {
        return jdbc.query("""
                SELECT t.trade_date AS at, t.security_id,
                       CASE WHEN t.side = 'B' THEN t.quantity_filled ELSE -t.quantity_filled END AS quantity_change,
                       CASE WHEN t.side = 'B' THEN -t.cash_amount ELSE t.cash_amount END AS cash_change
                FROM trade_details t WHERE t.account_id = ? AND t.trade_date >= ?
                UNION ALL
                SELECT coalesce(x.completed_at, x.transaction_date), NULL, 0,
                       CASE WHEN x.transaction_type IN ('WITHDRAWAL', 'FEE') THEN -x.transaction_amount ELSE x.transaction_amount END
                FROM transactions x
                WHERE x.account_id = ? AND x.transaction_status = 'COMPLETED' AND coalesce(x.completed_at, x.transaction_date) >= ?
                ORDER BY 1 DESC
                """, (r, row) -> new ValueEvent(instant(r, "at"), (Long) r.getObject("security_id", Long.class),
                r.getBigDecimal("quantity_change"), r.getBigDecimal("cash_change")),
                id, Timestamp.from(since), id, Timestamp.from(since));
    }

    public List<Instrument> instruments(long id) {
        return jdbc.query("""
                WITH ids AS (SELECT security_id FROM account_positions WHERE account_id = ?
                             UNION SELECT security_id FROM trade_details WHERE account_id = ?)
                SELECT i.security_id, s.ticker, s.quote_currency, coalesce(p.quantity, 0) AS quantity,
                       last.fx_rate_to_usd, coalesce(last.price_of_execution, p.average_price) AS fallback_usd_price
                FROM ids i JOIN securities s ON s.security_id = i.security_id
                LEFT JOIN account_positions p ON p.account_id = ? AND p.security_id = i.security_id
                LEFT JOIN LATERAL (SELECT d.fx_rate_to_usd, d.price_of_execution FROM trade_details d
                                   WHERE d.account_id = ? AND d.security_id = i.security_id
                                   ORDER BY d.trade_date DESC, d.trade_id DESC LIMIT 1) last ON true
                """, (r, row) -> new Instrument(r.getLong("security_id"), r.getString("ticker"),
                r.getString("quote_currency"), r.getBigDecimal("quantity"), r.getBigDecimal("fx_rate_to_usd"),
                r.getBigDecimal("fallback_usd_price")), id, id, id, id);
    }

    private <T> PageResponse<T> accountPage(String countSql, String sql, RowMapper<T> mapper, long id, int page, int size) {
        validatePage(page, size);
        long total = jdbc.queryForObject(countSql, Long.class, id);
        return PageResponse.of(jdbc.query(sql, mapper, id, size, (long) page * size), page, size, total);
    }
    private AccountDto accountRow(ResultSet r, int row) throws SQLException {
        if (r.getBigDecimal("cash_balance") == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ACCOUNT_BALANCE_MISSING", "Account balance is unavailable.");
        }
        var balance = new AccountBalanceDto(r.getLong("account_id"), r.getString("currency"),
                r.getBigDecimal("cash_balance"), r.getBigDecimal("reserved_cash"), r.getBigDecimal("available_cash"),
                instant(r, "balance_updated_date"));
        return new AccountDto(r.getLong("account_id"), r.getLong("user_id"), r.getString("currency"),
                instant(r, "created_date"), instant(r, "updated_date"), balance);
    }
    private CashTransactionDto transactionRow(ResultSet r, int row) throws SQLException {
        return new CashTransactionDto(r.getLong("transaction_id"), r.getLong("account_id"),
                r.getObject("client_request_id", UUID.class), r.getBigDecimal("transaction_amount"),
                CashTransactionType.valueOf(r.getString("transaction_type")),
                CashTransactionStatus.valueOf(r.getString("transaction_status")), instant(r, "transaction_date"),
                instant(r, "completed_at"), r.getString("failure_reason"));
    }
    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page must be nonnegative; size must be between 1 and 100.");
        }
    }
}
