package com.neueda.leap.team.repository;

import com.neueda.leap.team.dto.AdminDisputeDto;
import com.neueda.leap.team.dto.DisputeResponseDto;
import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.entity.enums.DisputeStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/** Cross-account dispute queue for admins. SQL fragments are fixed; every caller value is bound. */
@Repository
public class DisputeQueryRepository {
    private final JdbcTemplate jdbc;
    public DisputeQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String FROM = """
            FROM disputes d
            JOIN accounts a ON a.account_id = d.account_id
            JOIN users u ON u.user_id = a.user_id
            LEFT JOIN users adm ON adm.user_id = d.admin_id
            LEFT JOIN transactions t ON t.transaction_id = d.transaction_id
            LEFT JOIN trades tr ON tr.trade_id = d.trade_id
            LEFT JOIN executions e ON e.execution_id = tr.execution_id
            """;

    public PageResponse<AdminDisputeDto> search(DisputeStatus status, String query, int page, int size) {
        var where = new StringBuilder(" WHERE 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null) {
            where.append(" AND d.status = ?");
            params.add(status.name());
        }
        if (query != null && !query.isBlank()) {
            String q = query.strip();
            String pattern = "%" + q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append("""
                     AND (lower(u.name) LIKE ? OR lower(u.email) LIKE ? OR lower(d.dispute_type) LIKE ?
                          OR lower(coalesce(d.description, '')) LIKE ?
                          OR CAST(u.user_id AS TEXT) = ? OR CAST(d.dispute_id AS TEXT) = ? OR CAST(d.account_id AS TEXT) = ?)
                    """);
            for (int i = 0; i < 4; i++) params.add(pattern);
            for (int i = 0; i < 3; i++) params.add(q);
        }
        long count = jdbc.queryForObject("SELECT count(*) " + FROM + where, Long.class, params.toArray());
        params.add(size);
        params.add((long) page * size);
        var rows = jdbc.query("""
                SELECT d.dispute_id, d.account_id, u.user_id AS client_id, u.name AS client_name,
                       u.email AS client_email, d.admin_id, adm.name AS admin_name, d.transaction_id,
                       d.trade_id, d.dispute_type, d.description, d.status, d.date_created, d.date_resolved,
                       coalesce(t.transaction_amount, e.cash_amount) AS disputed_amount
                """ + FROM + where + " ORDER BY d.date_created DESC, d.dispute_id DESC LIMIT ? OFFSET ?",
                DisputeQueryRepository::row, params.toArray());
        var responses = responsesFor(rows.stream().map(AdminDisputeDto::disputeId).toList());
        var content = rows.stream().map(d -> new AdminDisputeDto(d.disputeId(), d.accountId(), d.clientId(),
                d.clientName(), d.clientEmail(), d.adminId(), d.adminName(), d.transactionId(), d.tradeId(),
                d.disputeType(), d.description(), d.disputedAmount(), d.status(), d.dateCreated(),
                d.dateResolved(), responses.getOrDefault(d.disputeId(), List.of()))).toList();
        return PageResponse.of(content, page, size, count);
    }

    /** Admin replies for each dispute, oldest first. */
    public Map<Long, List<DisputeResponseDto>> responsesFor(Collection<Long> disputeIds) {
        Map<Long, List<DisputeResponseDto>> result = new HashMap<>();
        if (disputeIds.isEmpty()) return result;
        String placeholders = String.join(",", Collections.nCopies(disputeIds.size(), "?"));
        jdbc.query("""
                SELECT r.response_id, r.dispute_id, r.admin_id, u.name AS admin_name, r.message, r.created_date
                FROM dispute_responses r JOIN users u ON u.user_id = r.admin_id
                WHERE r.dispute_id IN (""" + placeholders + ") ORDER BY r.created_date, r.response_id",
                (RowCallbackHandler) r -> result
                        .computeIfAbsent(r.getLong("dispute_id"), id -> new ArrayList<>())
                        .add(responseRow(r)),
                disputeIds.toArray());
        return result;
    }

    public DisputeResponseDto addResponse(long disputeId, long adminId, String message) {
        return jdbc.queryForObject("""
                WITH inserted AS (
                    INSERT INTO dispute_responses(dispute_id, admin_id, message) VALUES (?, ?, ?)
                    RETURNING response_id, admin_id, message, created_date
                )
                SELECT i.*, u.name AS admin_name FROM inserted i JOIN users u ON u.user_id = i.admin_id
                """, (r, rowNum) -> responseRow(r), disputeId, adminId, message);
    }

    private static DisputeResponseDto responseRow(ResultSet r) throws SQLException {
        return new DisputeResponseDto(r.getLong("response_id"), r.getLong("admin_id"), r.getString("admin_name"),
                r.getString("message"), r.getTimestamp("created_date").toInstant());
    }

    private static AdminDisputeDto row(ResultSet r, int rowNum) throws SQLException {
        return new AdminDisputeDto(r.getLong("dispute_id"), r.getLong("account_id"), r.getLong("client_id"),
                r.getString("client_name"), r.getString("client_email"), r.getObject("admin_id", Long.class),
                r.getString("admin_name"), r.getObject("transaction_id", Long.class),
                r.getObject("trade_id", Long.class), r.getString("dispute_type"), r.getString("description"),
                r.getBigDecimal("disputed_amount"), DisputeStatus.valueOf(r.getString("status")),
                r.getTimestamp("date_created").toInstant(), instant(r.getTimestamp("date_resolved")), List.of());
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
