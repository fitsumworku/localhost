package com.neueda.leap.team;

import com.neueda.leap.team.support.AccountTestSupport;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminQueueIntegrationTests extends AccountTestSupport {

    private tools.jackson.databind.JsonNode adminGet(String path, String... params) throws Exception {
        var request = get(path).header("Authorization", "Bearer " + adminToken);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return body(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private long fileTransactionDispute(long accountId, String bearer, String description) throws Exception {
        long transactionId = jdbc.queryForObject(
                "SELECT max(transaction_id) FROM transactions WHERE account_id = ?", Long.class, accountId);
        return body(mvc.perform(post("/accounts/{a}/disputes/transaction/{t}", accountId, transactionId)
                .header("Authorization", "Bearer " + bearer).contentType("application/json")
                .content(json.writeValueAsString(Map.of("disputeType", "DEPOSIT", "description", description))))
                .andExpect(status().isCreated()).andReturn()).path("disputeId").asLong();
    }

    @Test void adminListsDisputesAcrossAccountsWithClientAndAmount() throws Exception {
        jdbc.update("UPDATE users SET name = 'Owner Person' WHERE user_id = ?", owner);
        long ownerAccount = account(token);
        long otherAccount = account(otherToken);
        cash(ownerAccount, "deposits", "125.50");
        cash(otherAccount, "deposits", "10.00", java.util.UUID.randomUUID(), otherToken);
        long ownerDispute = fileTransactionDispute(ownerAccount, token, "Deposit never arrived");
        long otherDispute = fileTransactionDispute(otherAccount, otherToken, "Wrong amount credited");

        var all = adminGet("/disputes");
        assertThat(all.path("totalElements").asLong()).isEqualTo(2);
        // Newest first.
        assertThat(all.path("content").get(0).path("disputeId").asLong()).isEqualTo(otherDispute);
        var row = all.path("content").get(1);
        assertThat(row.path("clientId").asLong()).isEqualTo(owner);
        assertThat(row.path("clientName").asText()).isEqualTo("Owner Person");
        assertThat(row.path("accountId").asLong()).isEqualTo(ownerAccount);
        assertThat(row.path("disputedAmount").decimalValue()).isEqualByComparingTo(new BigDecimal("125.50"));
        assertThat(row.path("status").asText()).isEqualTo("OPEN");
        assertThat(row.path("adminName").isNull()).isTrue();

        var byName = adminGet("/disputes", "q", "owner per");
        assertThat(byName.path("content")).hasSize(1);
        assertThat(byName.path("content").get(0).path("disputeId").asLong()).isEqualTo(ownerDispute);
        assertThat(adminGet("/disputes", "q", Long.toString(other)).path("content").get(0).path("disputeId").asLong())
                .isEqualTo(otherDispute);
        assertThat(adminGet("/disputes", "q", "never arrived").path("totalElements").asLong()).isEqualTo(1);
        // LIKE wildcards in the search are literal.
        assertThat(adminGet("/disputes", "q", "%").path("totalElements").asLong()).isZero();

        mvc.perform(post("/accounts/{a}/disputes/{d}/assign-to-me", ownerAccount, ownerDispute)
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        var inReview = adminGet("/disputes", "status", "UNDER_REVIEW");
        assertThat(inReview.path("content")).hasSize(1);
        assertThat(inReview.path("content").get(0).path("adminId").asLong()).isEqualTo(admin);
        assertThat(adminGet("/disputes", "status", "open").path("content").get(0).path("disputeId").asLong())
                .isEqualTo(otherDispute);
    }

    @Test void adminRepliesAreShownToTheClientOnTheirDispute() throws Exception {
        jdbc.update("UPDATE users SET name = 'Support Admin' WHERE user_id = ?", admin);
        long account = account(token);
        cash(account, "deposits", "50.00");
        long dispute = fileTransactionDispute(account, token, "Deposit is missing");
        String path = "/accounts/" + account + "/disputes/" + dispute + "/responses";

        for (String message : new String[]{"  We are looking into this.  ", "Funds have been credited."}) {
            mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("message", message))))
                    .andExpect(status().isCreated());
        }

        var listed = body(mvc.perform(get("/accounts/{a}/disputes", account).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("content").get(0).path("responses");
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).path("message").asText()).isEqualTo("We are looking into this.");
        assertThat(listed.get(0).path("adminName").asText()).isEqualTo("Support Admin");
        assertThat(listed.get(1).path("message").asText()).isEqualTo("Funds have been credited.");
        var single = getJson("/accounts/" + account + "/disputes/" + dispute, token);
        assertThat(single.path("responses")).hasSize(2);
        assertThat(adminGet("/disputes").path("content").get(0).path("responses")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action_type = 'DISPUTE_RESPONDED'", Long.class))
                .isEqualTo(2);

        // Clients cannot reply, replies must have text, and the dispute must belong to the account.
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"message\":\"hello\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content("{\"message\":\"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content(json.writeValueAsString(Map.of("message", "x".repeat(2001))))).andExpect(status().isBadRequest());
        long otherAccount = account(otherToken);
        mvc.perform(post("/accounts/" + otherAccount + "/disputes/" + dispute + "/responses")
                .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content("{\"message\":\"hello\"}")).andExpect(status().isNotFound());
    }

    @Test void disputeQueueIsAdminOnlyAndValidatesInput() throws Exception {
        mvc.perform(get("/disputes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/disputes").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/disputes").param("status", "NOPE").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/disputes").param("size", "101").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test void adminSearchesClientsByNameEmailOrId() throws Exception {
        jdbc.update("UPDATE users SET name = 'Alice Smith' WHERE user_id = ?", owner);
        jdbc.update("UPDATE users SET name = 'Bob Jones' WHERE user_id = ?", other);

        var byName = adminGet("/users", "role", "CLIENT", "q", "alice");
        assertThat(byName.path("content")).hasSize(1);
        assertThat(byName.path("content").get(0).path("userId").asLong()).isEqualTo(owner);
        assertThat(adminGet("/users", "role", "CLIENT", "q", "OTHER@EXAMPLE").path("content").get(0)
                .path("userId").asLong()).isEqualTo(other);
        assertThat(adminGet("/users", "role", "CLIENT", "q", Long.toString(other)).path("content").get(0)
                .path("userId").asLong()).isEqualTo(other);
        assertThat(adminGet("/users", "role", "CLIENT", "q", "_").path("totalElements").asLong()).isZero();
        // The role filter still applies alongside search.
        assertThat(adminGet("/users", "role", "CLIENT", "q", "admin").path("totalElements").asLong()).isZero();

        mvc.perform(patch("/users/{id}/status", owner).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isOk());
        var blacklisted = adminGet("/users", "role", "CLIENT", "status", "SUSPENDED", "q", "smith");
        assertThat(blacklisted.path("content")).hasSize(1);
        // Blacklisting revokes the client's existing token immediately.
        mvc.perform(get("/accounts").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
}
