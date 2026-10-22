package com.neueda.leap.team.order;

import com.neueda.leap.team.market.QuoteResult;
import com.neueda.leap.team.support.AccountTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(OrderIntegrationTests.QuotesConfig.class)
class OrderAuditIntegrationTests extends AccountTestSupport {
    @Autowired OrderWorker worker;
    @Autowired OrderExecutionService execution;
    @Autowired OrderIntegrationTests.FakeQuotes quotes;
    long account, security;

    @BeforeEach void fixture() throws Exception {
        quotes.overrides.clear();
        account = account(token);
        security = security();
        cash(account, "deposits", "1000");
    }

    @Test void acceptedAndFilledOrderHasUserAndSystemEventsAndReplayDoesNotDuplicateThem() throws Exception {
        UUID request = UUID.randomUUID();
        long id = place(request, 202);
        place(request, 200);
        var claim = execution.claim(id).orElseThrow();
        var prices = quotes.quotes(Set.of("TEST"));
        execution.complete(claim, prices);
        execution.complete(claim, prices);
        place(request, 200);
        assertThat(events("ORDER_ACCEPTED")).isEqualTo(1);
        assertThat(events("ORDER_EXECUTION_STARTED")).isEqualTo(1);
        assertThat(events("ORDER_FILLED")).isEqualTo(1);
        var accepted = jdbc.queryForMap("SELECT actor_user_id,subject_user_id,actor_type FROM audit_logs WHERE action_type='ORDER_ACCEPTED'");
        assertThat(((Number) accepted.get("actor_user_id")).longValue()).isEqualTo(owner);
        assertThat(accepted.get("actor_type")).isEqualTo("USER");
        var filled = jdbc.queryForMap("SELECT actor_user_id,subject_user_id,actor_type,record_key::text AS key,old_value::text AS before,new_value::text AS after FROM audit_logs WHERE action_type='ORDER_FILLED'");
        assertThat(filled.get("actor_user_id")).isNull();
        assertThat(filled.get("actor_type")).isEqualTo("SYSTEM");
        assertThat(((Number) filled.get("subject_user_id")).longValue()).isEqualTo(owner);
        assertThat(json.readTree(filled.get("key").toString()).path("order_id").asLong()).isEqualTo(id);
        JsonNode before = json.readTree(filled.get("before").toString());
        JsonNode after = json.readTree(filled.get("after").toString());
        assertThat(before.path("cashBalance").decimalValue()).isEqualByComparingTo("1000");
        assertThat(after.path("cashBalance").decimalValue()).isEqualByComparingTo("750");
        assertThat(after.path("orderStatus").asText()).isEqualTo("FILLED");
        assertThat(after.path("tradeId").asLong()).isPositive();
        assertThat(after.path("executionId").asLong()).isPositive();
        var position = jdbc.queryForMap("SELECT quantity,average_price FROM account_positions WHERE account_id=? AND security_id=?", account, security);
        assertThat(after.path("positionQuantity").decimalValue()).isEqualByComparingTo((BigDecimal) position.get("quantity"));
        assertThat(after.path("averagePrice").decimalValue()).isEqualByComparingTo((BigDecimal) position.get("average_price"));
        var ledger = jdbc.queryForMap("SELECT trade_id,running_balance FROM cash_ledger WHERE trade_id=?", after.path("tradeId").asLong());
        assertThat(((Number) ledger.get("trade_id")).longValue()).isEqualTo(after.path("tradeId").asLong());
        assertThat((BigDecimal) ledger.get("running_balance")).isEqualByComparingTo(after.path("cashBalance").decimalValue());
        assertThat(count("trades", account)).isEqualTo(1);
    }

    @Test void cancellationAndRejectedSubmissionAreRecordedOnce() throws Exception {
        long id = place(UUID.randomUUID(), 202);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/accounts/{a}/orders/{o}/cancel", account, id)
                    .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        }
        assertThat(events("ORDER_CANCELLED")).isEqualTo(1);
        jdbc.update("UPDATE securities SET status='HALTED' WHERE security_id=?", security);
        UUID rejected = UUID.randomUUID();
        place(rejected, 409);
        place(rejected, 409);
        assertThat(events("ORDER_REJECTED")).isEqualTo(1);
        assertThat(events("ORDER_FILLED")).isZero();
    }

    @Test void overnightWaitAndRetryRecordReasonsWithoutPretendingTradeFilled() throws Exception {
        quotes.overrides.put("TEST", OrderIntegrationTests.quote("TEST", "100", "USD", "closed", false));
        long id = place(UUID.randomUUID(), 202);
        worker.runOnce();
        assertThat(events("ORDER_WAITING")).isEqualTo(1);
        assertThat(events("ORDER_FILLED")).isZero();
        assertThat(count("trades", account)).isZero();
        assertThat(balance(account)).isEqualByComparingTo("1000");
        long waitingEvents = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isEqualTo(waitingEvents);
        jdbc.update("UPDATE orders SET next_attempt_at=? WHERE order_id=?", Timestamp.from(Instant.now().minusSeconds(1)), id);
        quotes.overrides.put("TEST", QuoteResult.error("RATE_LIMITED", 60));
        worker.runOnce();
        assertThat(events("EXECUTION_FAILED")).isEqualTo(1);
        assertThat(events("ORDER_RETRY_SCHEDULED")).isEqualTo(1);
        assertThat(events("ORDER_FILLED")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM order_reservations WHERE order_id=?", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test void auditFailureRollsBackEntireSettlementAndSameClaimCanThenSucceed() throws Exception {
        long id = place(UUID.randomUUID(), 202);
        var claim = execution.claim(id).orElseThrow();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT audit_test_reject_fill CHECK(action_type<>'ORDER_FILLED') NOT VALID");
        try {
            assertThatThrownBy(() -> execution.complete(claim, quotes.quotes(Set.of("TEST"))))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT audit_test_reject_fill");
        }
        assertThat(events("ORDER_FILLED")).isZero();
        assertThat(balance(account)).isEqualByComparingTo("1000");
        assertThat(count("trades", account)).isZero();
        assertThat(count("account_positions", account)).isZero();
        assertThat(count("cash_ledger", account)).isEqualTo(1); // The original deposit only.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM executions WHERE order_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE order_id=?", String.class, id)).isEqualTo("IN_EXECUTION");
        execution.complete(claim, quotes.quotes(Set.of("TEST")));
        assertThat(events("ORDER_FILLED")).isEqualTo(1);
        assertThat(balance(account)).isEqualByComparingTo("750");
        assertThat(count("cash_ledger", account)).isEqualTo(2);
    }

    @Test void recoveredLeaseIsAuditedButObsoleteWorkerCannotWriteAnEvent() throws Exception {
        long id = place(UUID.randomUUID(), 202);
        var old = execution.claim(id).orElseThrow();
        jdbc.update("UPDATE orders SET execution_lease_until=? WHERE order_id=?", Timestamp.from(Instant.now().minusSeconds(1)), id);
        var recovered = execution.claim(id).orElseThrow();
        assertThat(events("ORDER_LEASE_RECOVERED")).isEqualTo(1);
        long eventsBefore = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        execution.complete(old, quotes.quotes(Set.of("TEST")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isEqualTo(eventsBefore);
        execution.complete(recovered, quotes.quotes(Set.of("TEST")));
        assertThat(events("ORDER_FILLED")).isEqualTo(1);
    }

    private long place(UUID request, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/accounts/{id}/orders", account).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("clientRequestId", request,
                        "securityId", security, "side", "B", "requestedAmount", new BigDecimal("250")))))
                .andExpect(status().is(expectedStatus)).andReturn();
        return body(result).path("details").path("order").path("orderId").asLong();
    }
    private long events(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action_type=?", Long.class, action);
    }
}
