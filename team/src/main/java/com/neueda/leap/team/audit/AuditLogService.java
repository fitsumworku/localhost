package com.neueda.leap.team.audit;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes curated business events to OLTP, in the caller's transaction. */
@Service
public class AuditLogService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public AuditLogService(@Qualifier("jdbcTemplate") JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // Mandatory prevents an accidental standalone audit commit before a business rollback.
    // Callers must pass explicit safe snapshots, never an entity, credentials or a token.
    @Transactional(transactionManager = "transactionManager", propagation = Propagation.MANDATORY)
    public void record(Long actorUserId, Long subjectUserId, String affectedTable,
                       Map<String, ?> recordKey, String actionType,
                       Object oldValue, Object newValue) {
        insert(actorUserId, subjectUserId, affectedTable, recordKey, actionType, oldValue, newValue);
    }

    /** Called by the controller only AFTER the failed login transaction has unwound. */
    @Transactional(transactionManager = "transactionManager", propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(String normalizedEmail) {
        var users = jdbc.queryForList("SELECT user_id FROM users WHERE email = ?", Long.class,
                normalizedEmail == null ? "" : normalizedEmail.strip().toLowerCase(Locale.ROOT));
        Long subject = users.isEmpty() ? null : users.get(0);
        // A claimed email is not an authenticated actor. Never persist the submitted email/password.
        insert(null, subject, "authentication", Map.of("attempt_id", UUID.randomUUID().toString()),
                "LOGIN_FAILED", null, Map.of("reason", "INVALID_CREDENTIALS"));
    }

    private void insert(Long actorUserId, Long subjectUserId, String affectedTable,
                        Map<String, ?> recordKey, String actionType, Object oldValue, Object newValue) {
        if (affectedTable == null || affectedTable.isBlank() || affectedTable.length() > 100
                || actionType == null || actionType.isBlank() || actionType.length() > 40
                || recordKey == null || recordKey.isEmpty()) {
            throw new IllegalArgumentException("An audit event requires a table, record key and action.");
        }
        jdbc.update("""
                INSERT INTO audit_logs (actor_user_id, actor_type, subject_user_id,
                    affected_table, record_key, action_type, old_value, new_value)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """, actorUserId, actorUserId == null ? "SYSTEM" : "USER", subjectUserId,
                affectedTable, json.writeValueAsString(recordKey), actionType,
                oldValue == null ? null : json.writeValueAsString(oldValue),
                newValue == null ? null : json.writeValueAsString(newValue));
    }
}
