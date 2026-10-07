package com.neueda.leap.team;

import com.neueda.leap.team.support.AccountTestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountIntegrationTests extends AccountTestSupport {
    @Test void clientsCanOpenMultipleIndependentUsdAccounts() throws Exception {
        long first = account(token), second = account(token);
        assertThat(second).isNotEqualTo(first);
        var list = getJson("/accounts", token);
        assertThat(list.path("totalElements").asLong()).isEqualTo(2);
        assertThat(list.path("content").get(0).path("userId").asLong()).isEqualTo(owner);
        var detail = getJson("/accounts/" + first, token);
        assertThat(detail.path("currency").asText()).isEqualTo("USD");
        assertThat(detail.has("status")).isFalse();
        assertThat(detail.path("balance").path("cashBalance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(cash(first, "deposits", "50.00").getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(second)).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest @ValueSource(strings = {"{\"currency\":\"EUR\"}", "{\"userId\":123}",
            "{\"cashBalance\":500}", "{\"status\":\"ACTIVE\"}", "{\"currency\":\"\"}"})
    void creationRejectsUnsupportedCurrencyAndInjectedFields(String payload) throws Exception {
        mvc.perform(post("/accounts").header("Authorization", "Bearer " + token).contentType("application/json").content(payload))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class)).isZero();
    }

    @Test void clientListCannotBeRedirectedToAnotherUser() throws Exception {
        long a = account(token), b = account(otherToken);
        var own = getJson("/accounts?userId=" + owner, token);
        assertThat(own.path("totalElements").asLong()).isEqualTo(1);
        assertThat(own.path("content").get(0).path("accountId").asLong()).isEqualTo(a);
        mvc.perform(get("/accounts").param("userId", Long.toString(other)).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertThat(b).isNotEqualTo(a);
    }

    @ParameterizedTest @ValueSource(strings = {"", "/balance", "/positions", "/orders", "/trades", "/transactions", "/ledger", "/transactions/1"})
    void everyAccountReadChecksOwnership(String suffix) throws Exception {
        long id = account(otherToken);
        mvc.perform(get("/accounts/" + id + suffix).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/accounts")).andExpect(status().isUnauthorized());
    }

    @Test void adminCanInspectAccountsButCannotCreateOrMoveClientCash() throws Exception {
        long id = account(token);
        assertThat(getJson("/accounts", adminToken).path("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson("/accounts?userId=" + other, adminToken).path("totalElements").asLong()).isZero();
        getJson("/accounts/" + id + "/balance", adminToken);
        mvc.perform(post("/accounts").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        for (String kind : new String[]{"deposits", "withdrawals"}) {
            assertThat(cash(id, kind, "1.00", UUID.randomUUID(), adminToken).getResponse().getStatus()).isEqualTo(403);
        }
    }

    @Test void blacklistingAndLogoutProtectNewAccountEndpoints() throws Exception {
        long id = account(token);
        mvc.perform(patch("/users/{id}/status", owner).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        assertThat(cash(id, "deposits", "1.00").getResponse().getStatus()).isEqualTo(401);
        mvc.perform(get("/accounts").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        getJson("/accounts/" + id, adminToken);
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + otherToken)).andExpect(status().isNoContent());
        mvc.perform(post("/accounts").header("Authorization", "Bearer " + otherToken)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"?page=-1", "?size=0", "?size=101"})
    void paginationIsBounded(String query) throws Exception {
        mvc.perform(get("/accounts" + query).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test void creationRollsBackBothRowsWhenAuditingFails() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT fail_account_audit CHECK (action_type <> 'BALANCE_INITIALIZED')");
        try {
            mvc.perform(post("/accounts").header("Authorization", "Bearer " + token)).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM account_cash_balances", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT fail_account_audit"); }
    }

    @Test void missingBalanceIsNotPresentedAsZeroOrHiddenFromList() throws Exception {
        long id = account(token);
        jdbc.update("DELETE FROM account_cash_balances WHERE account_id=?", id);
        mvc.perform(get("/accounts/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ACCOUNT_BALANCE_MISSING"));
        mvc.perform(get("/accounts").header("Authorization", "Bearer " + token)).andExpect(status().isInternalServerError());
    }

    @Test void holdingsShowShareReservationsWithoutPretendingCostIsAMarketQuote() throws Exception {
        long id = account(token), sec = security();
        jdbc.update("INSERT INTO account_positions(account_id,security_id,quantity,average_price) VALUES (?,?,10,25)", id, sec);
        long order = reserve(id, sec, "S", "3");
        var position = getJson("/accounts/" + id + "/positions", token).path("content").get(0);
        assertThat(position.path("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(position.path("reservedQuantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(position.path("availableQuantity").decimalValue()).isEqualByComparingTo("7");
        assertThat(position.path("averagePrice").decimalValue()).isEqualByComparingTo("25");
        assertThat(position.path("accountCurrency").asText()).isEqualTo("USD");
        var orders = getJson("/accounts/" + id + "/orders", token);
        assertThat(orders.path("content").get(0).path("orderId").asLong()).isEqualTo(order);
        assertThat(getJson("/accounts/" + id + "/trades", token).path("totalElements").asLong()).isZero();
    }

    @Test void successfulFillHistoryRetainsStoredPricingAndIsScopedToAccount() throws Exception {
        long id = account(token), another = account(token), sec = security();
        long order = reserve(id, sec, "B", "50.00");
        jdbc.update("UPDATE orders SET order_status='FILLED',terminal_at=clock_timestamp(),updated_date=clock_timestamp() WHERE order_id=?", order);
        jdbc.update("UPDATE order_reservations SET status='CONSUMED',resolved_at=clock_timestamp() WHERE order_id=?", order);
        long execution = jdbc.queryForObject("""
                INSERT INTO executions(order_id,account_id,security_id,side,status_of_execution,
                  quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,quantity_filled,price_of_execution)
                VALUES (?,?,?,'B','FILLED',25,'USD',CURRENT_TIMESTAMP,'test-fixture',1,2,25) RETURNING execution_id
                """, Long.class, order, id, sec);
        long trade = jdbc.queryForObject("INSERT INTO trades(execution_id,account_id) VALUES (?,?) RETURNING trade_id", Long.class, execution, id);
        var fill = getJson("/accounts/" + id + "/trades", token).path("content").get(0);
        assertThat(fill.path("tradeId").asLong()).isEqualTo(trade);
        assertThat(fill.path("cashAmount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(fill.path("quoteSource").asText()).isEqualTo("test-fixture");
        assertThat(getJson("/accounts/" + another + "/trades", token).path("totalElements").asLong()).isZero();
    }

    @Test void angularPreflightAndGeneratedDocsIncludeAccountRoutes() throws Exception {
        mvc.perform(options("/accounts/1/deposits").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        var spec = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertThat(spec.path("paths").has("/accounts/{accountId}/withdrawals")).isTrue();
        assertThat(spec.path("paths").path("/accounts").path("post").path("security").isEmpty()).isFalse();
    }

    @Test void registrationAndLoginStillWorkAfterAuditServiceExtension() throws Exception {
        String request = "{\"name\":\"New client\",\"email\":\"new@example.test\",\"password\":\"New-Account-Password-123!\"}";
        mvc.perform(post("/auth/register").contentType("application/json").content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CLIENT"));
        var login = body(mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"email\":\"new@example.test\",\"password\":\"New-Account-Password-123!\"}"))
                .andExpect(status().isOk()).andReturn());
        account(login.path("accessToken").asText());
    }
}
