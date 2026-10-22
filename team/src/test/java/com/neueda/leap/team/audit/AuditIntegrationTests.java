package com.neueda.leap.team.audit;

import com.neueda.leap.team.support.AccountTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditIntegrationTests extends AccountTestSupport {
    @Autowired AuditLogService audit;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void auditApiPreservesExactMoneyAndShareDecimalPrecision() throws Exception {
        BigDecimal amount = new BigDecimal("999999999999999999.99");
        BigDecimal shares = new BigDecimal("1.123456789012");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                audit.record(owner, owner, "orders", Map.of("order_id", 1), "ORDER_FILLED", null,
                        Map.of("amount", amount, "shares", shares)));
        long id = jdbc.queryForObject("SELECT max(audit_id) FROM audit_logs", Long.class);
        for (String path : new String[]{"/audit-logs/" + id, "/audit-logs"}) {
            var result = mvc.perform(get(path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk()).andReturn();
            JsonNode response = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(result.getResponse().getContentAsString());
            JsonNode event = path.equals("/audit-logs") ? response.path("content").get(0) : response;
            assertThat(event.path("newValue").path("amount").decimalValue()).isEqualByComparingTo(amount);
            assertThat(event.path("newValue").path("shares").decimalValue()).isEqualByComparingTo(shares);
        }
    }

    @Test void auditRequiresTransactionAndRollsBackWithBusinessData() {
        assertThatThrownBy(() -> audit.record(owner, owner, "users", Map.of("user_id", owner),
                "USER_PROFILE_UPDATED", Map.of("name", "Test user"), Map.of("name", "After")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            jdbc.update("UPDATE users SET name='After' WHERE user_id=?", owner);
            audit.record(owner, owner, "users", Map.of("user_id", owner), "USER_PROFILE_UPDATED",
                    Map.of("name", "Test user"), Map.of("name", "After"));
            throw new IllegalStateException("Abort business transaction");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE user_id=?", String.class, owner)).isEqualTo("Test user");
        assertThat(events("USER_PROFILE_UPDATED")).isZero();
    }

    @Test void adminCanReadAuditHistoryButClientsAndAnonymousCannot() throws Exception {
        account(token);
        long id = jdbc.queryForObject("SELECT max(audit_id) FROM audit_logs", Long.class);
        for (String path : new String[]{"/audit-logs", "/audit-logs/" + id, "/users/" + owner + "/audit-logs"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        }
        mvc.perform(get("/audit-logs/999999").header("Authorization", "Bearer " + adminToken)).andExpect(status().isNotFound());
        mvc.perform(post("/audit-logs").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 405));
    }

    @Test void filtersPaginationAndUserHistoryUseSubjectRatherThanActor() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            for (int i = 0; i < 3; i++) {
                audit.record(admin, owner, "users", Map.of("user_id", owner), "USER_STATUS_CHANGED",
                        Map.of("status", "ACTIVE"), Map.of("status", "SUSPENDED"));
            }
            audit.record(other, other, "accounts", Map.of("account_id", 999), "ACCOUNT_CREATED", null, Map.of("currency", "USD"));
        });
        jdbc.update("UPDATE audit_logs SET timestamp=?", Timestamp.from(Instant.parse("2026-10-01T12:00:00Z")));
        var page = body(mvc.perform(get("/audit-logs").header("Authorization", "Bearer " + adminToken)
                .param("actorUserId", Long.toString(admin)).param("subjectUserId", Long.toString(owner))
                .param("affectedTable", "users").param("actionType", "USER_STATUS_CHANGED")
                .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-02T00:00:00Z")
                .param("page", "1").param("size", "2")).andExpect(status().isOk()).andReturn());
        assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        assertThat(page.path("content").size()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("actorUserId").asLong()).isEqualTo(admin);
        assertThat(page.path("content").get(0).path("subjectUserId").asLong()).isEqualTo(owner);
        assertThat(getJson("/users/" + owner + "/audit-logs", adminToken).path("totalElements").asLong()).isEqualTo(3);
        var empty = body(mvc.perform(get("/audit-logs").header("Authorization", "Bearer " + adminToken)
                .param("from", "2026-10-03T00:00:00Z")).andExpect(status().isOk()).andReturn());
        assertThat(empty.path("totalElements").asLong()).isZero();
        // Stable audit-ID tie-breaker keeps equal timestamps from repeating across pages.
        var first = getJson("/audit-logs?page=0&size=2", adminToken).path("content");
        var second = getJson("/audit-logs?page=1&size=2", adminToken).path("content");
        assertThat(first.get(1).path("auditId").asLong()).isGreaterThan(second.get(0).path("auditId").asLong());
    }

    @Test void invalidFiltersAndPageBoundsReturnBadRequest() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=101", "actorUserId=-1",
                "from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z", "from=not-an-instant"}) {
            mvc.perform(get("/audit-logs?" + query).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void registerLoginAndLogoutRecordSafeSnapshotsAndRevokeToken() throws Exception {
        String password = "Audit-Test-Password-123!";
        String email = "new-client@example.test";
        var registration = body(mvc.perform(post("/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "New client", "email", email, "password", password))))
                .andExpect(status().isCreated()).andReturn());
        long id = registration.path("userId").asLong();
        var login = body(mvc.perform(post("/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn());
        String accessToken = login.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken)).andExpect(status().isNoContent());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken)).andExpect(status().isUnauthorized());
        for (String action : new String[]{"USER_REGISTERED", "LOGIN_SUCCEEDED", "LOGOUT"}) {
            assertThat(events(action)).isEqualTo(1);
            var row = jdbc.queryForMap("SELECT actor_user_id,subject_user_id,actor_type FROM audit_logs WHERE action_type=?", action);
            assertThat(((Number) row.get("actor_user_id")).longValue()).isEqualTo(id);
            assertThat(((Number) row.get("subject_user_id")).longValue()).isEqualTo(id);
            assertThat(row.get("actor_type")).isEqualTo("USER");
        }
        String snapshots = jdbc.queryForObject("SELECT string_agg(coalesce(old_value::text,'') || coalesce(new_value::text,''),' ') FROM audit_logs", String.class);
        assertThat(snapshots).doesNotContain(password, accessToken, "password_hash", "passwordHash", "tokenVersion", "$2a$", "$2b$");
    }

    @Test void rejectedLoginIsRecordedWithoutAnAuthenticatedActorOrSubmittedCredentials() throws Exception {
        for (String email : new String[]{"owner@example.test", "unknown@example.test"}) {
            mvc.perform(post("/auth/login").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("email", email, "password", "Wrong-Password-123!"))))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(events("LOGIN_FAILED")).isEqualTo(2);
        var rows = jdbc.queryForList("SELECT actor_user_id,subject_user_id,actor_type,new_value::text AS snapshot FROM audit_logs ORDER BY audit_id");
        assertThat(rows.get(0).get("actor_user_id")).isNull();
        assertThat(rows.get(0).get("actor_type")).isEqualTo("SYSTEM");
        assertThat(((Number) rows.get(0).get("subject_user_id")).longValue()).isEqualTo(owner);
        assertThat(rows.get(1).get("subject_user_id")).isNull();
        assertThat(rows.toString()).doesNotContain("Wrong-Password-123!", "unknown@example.test", "owner@example.test");
    }

    @Test void statusChangesDistinguishAdminActorFromClientSubjectAndIgnoreNoOp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/users/{id}/status", owner).header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content("{\"status\":\"SUSPENDED\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(events("USER_STATUS_CHANGED")).isEqualTo(1);
        var event = jdbc.queryForMap("SELECT actor_user_id,subject_user_id,old_value::text AS before,new_value::text AS after FROM audit_logs WHERE action_type='USER_STATUS_CHANGED'");
        assertThat(((Number) event.get("actor_user_id")).longValue()).isEqualTo(admin);
        assertThat(((Number) event.get("subject_user_id")).longValue()).isEqualTo(owner);
        assertThat(json.readTree((String) event.get("before")).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(json.readTree((String) event.get("after")).path("status").asText()).isEqualTo("SUSPENDED");
    }

    @Test void profileUpdateAndAdminSessionRevocationAreAudited() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/users/{id}", owner).header("Authorization", "Bearer " + token)
                    .contentType("application/json").content("{\"name\":\"Updated name\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/users/{id}/revoke-sessions", owner).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertThat(events("USER_PROFILE_UPDATED")).isEqualTo(1);
        assertThat(events("USER_SESSIONS_REVOKED")).isEqualTo(1);
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void cashRetriesDoNotDuplicateAuditAndFailedOperationsDoNotChangeBalance() throws Exception {
        long account = account(token);
        UUID deposit = UUID.randomUUID();
        assertThat(cash(account, "deposits", "100", deposit, token).getResponse().getStatus()).isEqualTo(201);
        assertThat(cash(account, "deposits", "100.00", deposit, token).getResponse().getStatus()).isEqualTo(200);
        UUID failed = UUID.randomUUID();
        assertThat(cash(account, "withdrawals", "150", failed, token).getResponse().getStatus()).isEqualTo(409);
        assertThat(cash(account, "withdrawals", "150", failed, token).getResponse().getStatus()).isEqualTo(409);
        assertThat(cash(account, "withdrawals", "25").getResponse().getStatus()).isEqualTo(201);
        assertThat(events("ACCOUNT_CREATED")).isEqualTo(1);
        assertThat(events("CASH_DEPOSITED")).isEqualTo(1);
        assertThat(events("CASH_WITHDRAWN")).isEqualTo(1);
        assertThat(events("CASH_OPERATION_FAILED")).isEqualTo(1);
        assertThat(balance(account)).isEqualByComparingTo("75");
        assertThat(count("cash_ledger", account)).isEqualTo(2);
    }

    @Test void auditWriteFailureRollsBackCashTransactionAndLedger() throws Exception {
        long account = account(token);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT audit_test_reject_cash CHECK(action_type<>'CASH_DEPOSITED') NOT VALID");
        try {
            var result = cash(account, "deposits", "100");
            assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(500);
        } finally {
            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT audit_test_reject_cash");
        }
        assertThat(balance(account)).isZero();
        assertThat(count("transactions", account)).isZero();
        assertThat(count("cash_ledger", account)).isZero();
        assertThat(events("CASH_DEPOSITED")).isZero();
    }

    @Test void disputeLifecycleRecordsActorAndOwnerAndSkipsRepeatedChanges() throws Exception {
        long account = account(token);
        cash(account, "deposits", "100");
        long transaction = jdbc.queryForObject("SELECT transaction_id FROM transactions WHERE account_id=?", Long.class, account);
        String sensitiveDescription = "Private customer explanation";
        var filed = body(mvc.perform(post("/accounts/{a}/disputes/transaction/{t}", account, transaction)
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("disputeType", "INCORRECT_AMOUNT", "description", sensitiveDescription))))
                .andExpect(status().isCreated()).andReturn());
        long dispute = filed.path("disputeId").asLong();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/accounts/{a}/disputes/{d}/assign-to-me", account, dispute)
                    .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
            mvc.perform(patch("/accounts/{a}/disputes/{d}/status", account, dispute)
                    .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                    .content("{\"disputeStatus\":\"RESOLVED\"}")).andExpect(status().isOk());
        }
        assertThat(events("DISPUTE_FILED")).isEqualTo(1);
        assertThat(events("DISPUTE_ASSIGNED")).isEqualTo(1);
        assertThat(events("DISPUTE_STATUS_CHANGED")).isEqualTo(1);
        var event = jdbc.queryForMap("SELECT actor_user_id,subject_user_id,new_value::text AS snapshot FROM audit_logs WHERE action_type='DISPUTE_STATUS_CHANGED'");
        assertThat(((Number) event.get("actor_user_id")).longValue()).isEqualTo(admin);
        assertThat(((Number) event.get("subject_user_id")).longValue()).isEqualTo(owner);
        assertThat(event.get("snapshot").toString()).doesNotContain(sensitiveDescription);
        assertThat(json.readTree(event.get("snapshot").toString()).path("status").asText()).isEqualTo("RESOLVED");
    }

    private long events(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action_type=?", Long.class, action);
    }
}
