package com.neueda.leap.team.audit;

import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.exception.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(transactionManager = "transactionManager", readOnly = true,
        isolation = Isolation.REPEATABLE_READ)
public class AuditLogQueryService {
    private static final String COLUMNS = "audit_id, actor_user_id, actor_type, subject_user_id, "
            + "affected_table, record_key, action_type, old_value, new_value, timestamp";
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public AuditLogQueryService(@Qualifier("jdbcTemplate") JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public PageResponse<AuditLogDto> list(Long actorUserId, Long subjectUserId, String affectedTable,
                                          String actionType, Instant from, Instant to, int page, int size) {
        positiveId(actorUserId);
        positiveId(subjectUserId);
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("page must be nonnegative and size must be between 1 and 100.");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw badRequest("from must be earlier than to.");
        }
        String table = filter(affectedTable, 100);
        String action = filter(actionType, 40);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (actorUserId != null) { where.append(" AND actor_user_id = ?"); args.add(actorUserId); }
        if (subjectUserId != null) { where.append(" AND subject_user_id = ?"); args.add(subjectUserId); }
        // Case-insensitive filters also support the capitalization in older seeded audit rows.
        if (table != null) { where.append(" AND lower(affected_table) = ?"); args.add(table.toLowerCase(Locale.ROOT)); }
        if (action != null) { where.append(" AND upper(action_type) = ?"); args.add(action.toUpperCase(Locale.ROOT)); }
        if (from != null) { where.append(" AND timestamp >= ?"); args.add(Timestamp.from(from)); }
        if (to != null) { where.append(" AND timestamp < ?"); args.add(Timestamp.from(to)); }
        long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM audit_logs" + where
                + " ORDER BY timestamp DESC, audit_id DESC LIMIT ? OFFSET ?", this::map, args.toArray());
        return PageResponse.of(rows, page, size, total);
    }

    public PageResponse<AuditLogDto> forUser(Long userId, String affectedTable, String actionType,
                                             Instant from, Instant to, int page, int size) {
        positiveId(userId);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE user_id = ?)",
                Boolean.class, userId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found.");
        }
        return list(null, userId, affectedTable, actionType, from, to, page, size);
    }

    public AuditLogDto get(Long auditId) {
        positiveId(auditId);
        return jdbc.query("SELECT " + COLUMNS + " FROM audit_logs WHERE audit_id = ?", this::map, auditId)
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "AUDIT_LOG_NOT_FOUND", "Audit log not found."));
    }

    private AuditLogDto map(ResultSet rs, int row) throws SQLException {
        return new AuditLogDto(rs.getLong("audit_id"), rs.getObject("actor_user_id", Long.class),
                rs.getString("actor_type"), rs.getObject("subject_user_id", Long.class),
                rs.getString("affected_table"), tree(rs.getString("record_key")), rs.getString("action_type"),
                tree(rs.getString("old_value")), tree(rs.getString("new_value")), rs.getTimestamp("timestamp").toInstant());
    }

    private JsonNode tree(String value) {
        // Audit snapshots include NUMERIC cash/share values; do not round them through a double.
        return value == null ? null : json.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value);
    }

    private static String filter(String value, int max) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > max) throw badRequest("Invalid audit filter.");
        return trimmed;
    }

    private static void positiveId(Long id) {
        if (id != null && id <= 0) throw badRequest("IDs must be positive.");
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_QUERY", message);
    }
}
