package com.neueda.leap.team.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;
    public AuditService(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUserEvent(Long actorId, long subjectId, String action,
                                Map<String, ?> oldValue, Map<String, ?> newValue) {
        write(actorId, subjectId, "users", Map.of("user_id", subjectId), action, oldValue, newValue);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEntityEvent(Long actorId, long subjectId, String table, Map<String, ?> recordKey,
                                  String action, Map<String, ?> oldValue, Map<String, ?> newValue) {
        write(actorId, subjectId, table, recordKey, action, oldValue, newValue);
    }

    private void write(Long actorId, long subjectId, String table, Map<String, ?> recordKey,
                       String action, Map<String, ?> oldValue, Map<String, ?> newValue) {
        // Callers provide an explicit safe field allowlist, never entities or request DTOs.
        jdbc.update("""
                INSERT INTO audit_logs
                  (actor_user_id, actor_type, subject_user_id, affected_table, record_key,
                   action_type, old_value, new_value, timestamp)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """, actorId, actorId == null ? "SYSTEM" : "USER", subjectId,
                table, json.writeValueAsString(recordKey), action,
                oldValue == null ? null : json.writeValueAsString(oldValue),
                newValue == null ? null : json.writeValueAsString(newValue), Timestamp.from(clock.instant()));
    }
}
