package com.neueda.leap.team.order;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.exception.ApiException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;
    public OrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    int update(String sql,Object... args) { return jdbc.update(sql,args); }
    long insert(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    private static final String ROW = """
            SELECT o.*, a.user_id, s.ticker, s.name, s.asset_type, s.exchange,
                   s.quote_currency, s.base_currency, s.status AS security_status
            FROM orders o JOIN accounts a ON a.account_id=o.account_id
            JOIN securities s ON s.security_id=o.security_id
            """;
    Optional<OrderRow> find(long id) { return jdbc.query(ROW+" WHERE o.order_id=?", this::row, id).stream().findFirst(); }
    Optional<OrderRow> findRequest(long account, UUID request) {
        return jdbc.query(ROW+" WHERE o.account_id=? AND o.client_request_id=?", this::row, account, request).stream().findFirst();
    }
    OrderRow locked(long id) {
        return jdbc.query(ROW+" WHERE o.order_id=? FOR UPDATE OF o", this::row, id).stream().findFirst().orElseThrow(OrderRepository::notFound);
    }
    BigDecimal lockBalance(long account) {
        return jdbc.queryForObject("SELECT cash_balance FROM account_cash_balances WHERE account_id=? FOR UPDATE", BigDecimal.class, account);
    }
    void lockOwner(long owner) { jdbc.queryForObject("SELECT user_id FROM users WHERE user_id=? FOR UPDATE", Long.class, owner); }
    BigDecimal reservedCash(long account) {
        return jdbc.queryForObject("SELECT coalesce(sum(reserved_cash),0) FROM order_reservations WHERE account_id=? AND side='B' AND status='ACTIVE'", BigDecimal.class, account);
    }
    BigDecimal reservedQuantity(long account, long security) {
        return jdbc.queryForObject("SELECT coalesce(sum(reserved_quantity),0) FROM order_reservations WHERE account_id=? AND security_id=? AND side='S' AND status='ACTIVE'", BigDecimal.class, account, security);
    }
    record Position(BigDecimal quantity, BigDecimal average) {}
    Position position(long account, long security) {
        return jdbc.query("SELECT quantity,average_price FROM account_positions WHERE account_id=? AND security_id=?",
                (r,n) -> new Position(r.getBigDecimal(1),r.getBigDecimal(2)),account,security).stream().findFirst()
                .orElse(new Position(BigDecimal.ZERO,BigDecimal.ZERO));
    }
    public SecurityDto security(long id, boolean lock) {
        return jdbc.query("SELECT *,status AS security_status FROM securities WHERE security_id=?"+(lock?" FOR SHARE":""),
                this::securityRow,id).stream().findFirst().orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND,"SECURITY_NOT_FOUND","Security not found."));
    }
    public PageResponse<SecurityDto> securities(String q, String status, int page, int size) {
        page(page,size);
        String search=q==null?"":q.strip();
        if(search.length()>80 || !Set.of("ACTIVE","HALTED","DELISTED","ALL").contains(status))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","Invalid security filter.");
        String where=" WHERE (?='ALL' OR status=?) AND (strpos(lower(ticker),lower(?))>0 OR strpos(lower(name),lower(?))>0)";
        long total=jdbc.queryForObject("SELECT count(*) FROM securities"+where,Long.class,status,status,search,search);
        var content=jdbc.query("SELECT *,status AS security_status FROM securities"+where+" ORDER BY ticker,security_id LIMIT ? OFFSET ?",
                this::securityRow,status,status,search,search,size,(long)page*size);
        return PageResponse.of(content,page,size,total);
    }
    public List<Long> due(Instant now, int limit) {
        return jdbc.query("""
                SELECT order_id FROM orders WHERE (order_status='ACCEPTED' AND next_attempt_at<=?)
                  OR (order_status='IN_EXECUTION' AND execution_lease_until<=?)
                ORDER BY coalesce(execution_lease_until,next_attempt_at),order_id LIMIT ?
                """,(r,n)->r.getLong(1),ts(now),ts(now),limit);
    }
    OrderDetailsDto details(OrderRow o) {
        var reservation=jdbc.query("SELECT * FROM order_reservations WHERE order_id=?",(r,n)->
                new OrderDetailsDto.Reservation(r.getString("status"),r.getBigDecimal("reserved_cash"),r.getBigDecimal("reserved_quantity"),
                        instant(r,"created_date"),instant(r,"resolved_at")),o.id()).stream().findFirst().orElse(null);
        return new OrderDetailsDto(new AccountOrderDto(o.id(),o.accountId(),o.securityId(),o.security().ticker(),o.security().name(),
                o.clientRequestId(),o.side(),o.amount(),o.quantity(),o.status(),o.createdAt(),o.updatedAt(),o.acceptedAt(),o.terminalAt(),o.rejectionReason()),
                reservation,o.nextAttemptAt(),o.attempts(),o.message());
    }
    PageResponse<ExecutionDto> executions(long id, int page, int size) {
        page(page,size);
        long total=jdbc.queryForObject("SELECT count(*) FROM executions WHERE order_id=?",Long.class,id);
        var content=jdbc.query("SELECT * FROM executions WHERE order_id=? ORDER BY execution_id DESC LIMIT ? OFFSET ?",(r,n)->
                new ExecutionDto(r.getLong("execution_id"),id,r.getString("status_of_execution"),instant(r,"started_at"),instant(r,"finished_at"),
                        r.getBigDecimal("quantity_filled"),r.getBigDecimal("price_of_execution"),r.getBigDecimal("cash_amount"),r.getBigDecimal("quote_price"),
                        r.getString("quote_currency"),instant(r,"quote_timestamp"),r.getString("quote_source"),r.getBigDecimal("fx_rate_to_usd"),
                        instant(r,"fx_quote_timestamp"),r.getString("fx_source"),r.getString("failure_reason")),id,size,(long)page*size);
        return PageResponse.of(content,page,size,total);
    }
    private OrderRow row(ResultSet r,int n)throws SQLException {
        return new OrderRow(r.getLong("order_id"),r.getLong("account_id"),r.getLong("user_id"),r.getLong("security_id"),r.getObject("client_request_id",UUID.class),
                r.getString("side"),r.getBigDecimal("requested_amount"),r.getBigDecimal("quantity_ordered"),r.getString("order_status"),
                instant(r,"created_date"),instant(r,"updated_date"),instant(r,"accepted_at"),instant(r,"terminal_at"),r.getString("rejection_reason"),
                instant(r,"next_attempt_at"),r.getObject("execution_token",UUID.class),instant(r,"execution_lease_until"),r.getInt("execution_attempts"),
                r.getString("last_execution_message"),securityRow(r,n));
    }
    private SecurityDto securityRow(ResultSet r,int n)throws SQLException {
        return new SecurityDto(r.getLong("security_id"),r.getString("ticker"),r.getString("name"),r.getString("asset_type"),r.getString("exchange"),
                r.getString("quote_currency"),r.getString("base_currency"),r.getString("security_status"));
    }
    static Timestamp ts(Instant value) { return value==null?null:Timestamp.from(value); }
    private static Instant instant(ResultSet r,String name)throws SQLException { var t=r.getTimestamp(name);return t==null?null:t.toInstant(); }
    static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found."); }
    private static void page(int page,int size) {
        if(page<0||size<1||size>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","page must be nonnegative; size must be between 1 and 100.");
    }
}
