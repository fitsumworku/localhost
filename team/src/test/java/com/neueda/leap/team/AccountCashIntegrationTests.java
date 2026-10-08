package com.neueda.leap.team;

import com.neueda.leap.team.support.AccountTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountCashIntegrationTests extends AccountTestSupport {
    @Test void cashMovementUpdatesTransactionAndBalanceTogether() throws Exception {
        long id = account(token);
        var deposit = cash(id, "deposits", "125.75");
        assertThat(deposit.getResponse().getStatus()).isEqualTo(201);
        var tx = body(deposit).path("transaction");
        assertThat(tx.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(balance(id)).isEqualByComparingTo("125.75");
        assertThat(tx.has("ledgerId")).isFalse();
        assertThat(tx.has("balanceAfter")).isFalse();
        assertThat(cash(id, "withdrawals", "25.25").getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(id)).isEqualByComparingTo("100.50");
        assertThat(count("transactions", id)).isEqualTo(2);
        var history = getJson("/accounts/" + id + "/transactions", token).path("content");
        assertThat(history.get(0).path("type").asText()).isEqualTo("WITHDRAWAL");
        assertThat(history.get(0).path("amount").decimalValue()).isEqualByComparingTo("25.25");
        getJson(deposit.getResponse().getHeader("Location"), token);
    }

    @Test void retryReturnsOriginalTransactionWithoutDuplicateMoney() throws Exception {
        long id = account(token); UUID key = UUID.randomUUID();
        var original = cash(id, "deposits", "10.10", key, token);
        cash(id, "deposits", "1.00");
        var repeated = cash(id, "deposits", "10.1", key, token);
        assertThat(repeated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(repeated).path("replayed").asBoolean()).isTrue();
        assertThat(body(repeated).path("transaction")).isEqualTo(body(original).path("transaction"));
        assertThat(balance(id)).isEqualByComparingTo("11.10");
        assertThat(count("transactions", id)).isEqualTo(2);
    }

    @Test void reusedKeyWithDifferentAmountOrOperationIsRejected() throws Exception {
        long id = account(token); UUID key = UUID.randomUUID();
        cash(id, "deposits", "10.00", key, token);
        for (var changed : List.of(cash(id, "deposits", "20.00", key, token), cash(id, "withdrawals", "10.00", key, token))) {
            assertThat(changed.getResponse().getStatus()).isEqualTo(409);
            assertThat(body(changed).path("code").asText()).isEqualTo("REQUEST_ID_REUSED");
        }
        assertThat(balance(id)).isEqualByComparingTo("10.00");
    }

    @Test void requestIdsAreScopedToAccount() throws Exception {
        long a = account(token), b = account(token); UUID key = UUID.randomUUID();
        assertThat(cash(a, "deposits", "20.00", key, token).getResponse().getStatus()).isEqualTo(201);
        assertThat(cash(b, "deposits", "20.00", key, token).getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(a)).isEqualByComparingTo("20.00");
        assertThat(balance(b)).isEqualByComparingTo("20.00");
    }

    @Test void insufficientWithdrawalCommitsFailureButNoCashPosting() throws Exception {
        long id = account(token); UUID key = UUID.randomUUID();
        var failed = cash(id, "withdrawals", "10.00", key, token);
        assertThat(failed.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(failed).path("transaction").path("failureReason").asText()).isEqualTo("INSUFFICIENT_AVAILABLE_CASH");
        assertThat(count("transactions", id)).isEqualTo(1);
        cash(id, "deposits", "100.00");
        var retry = cash(id, "withdrawals", "10.00", key, token);
        assertThat(retry.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(retry).path("replayed").asBoolean()).isTrue();
        assertThat(balance(id)).isEqualByComparingTo("100.00");
    }

    @Test void withdrawalsRespectOnlyActiveBuyReservationsIncludingOvernightOrders() throws Exception {
        long id = account(token), sec = security();
        cash(id, "deposits", "100.00");
        long reserved = reserve(id, sec, "B", "70.00");
        var availability = getJson("/accounts/" + id + "/balance", token);
        assertThat(availability.path("cashBalance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(availability.path("reservedCash").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(availability.path("availableCash").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(cash(id, "withdrawals", "30.01").getResponse().getStatus()).isEqualTo(409);
        assertThat(cash(id, "withdrawals", "30.00").getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(id)).isEqualByComparingTo("70.00");
        jdbc.update("UPDATE order_reservations SET status='RELEASED',resolved_at=clock_timestamp() WHERE order_id=?", reserved);
        assertThat(cash(id, "withdrawals", "70.00").getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(id)).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1.00", "0.001", "1000000000000000000.00"})
    void invalidAmountsHaveNoFinancialSideEffects(String amount) throws Exception {
        long id = account(token);
        assertThat(cash(id, "deposits", amount).getResponse().getStatus()).isEqualTo(400);
        assertThat(count("transactions", id)).isZero();
        assertThat(balance(id)).isEqualByComparingTo("0");
    }

    @Test void missingIdAndInjectedFieldsAreRejected() throws Exception {
        long id = account(token);
        for (String payload : List.of("{\"amount\":10}", "{\"clientRequestId\":\"not-a-uuid\",\"amount\":10}",
                "{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"amount\":10,\"type\":\"INTEREST\"}")) {
            mvc.perform(post("/accounts/{id}/deposits", id).header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        }
        assertThat(count("transactions", id)).isZero();
    }

    @Test void clientCannotDepositWithdrawOrReadTransactionsThroughAnotherAccount() throws Exception {
        long a = account(token), b = account(otherToken);
        var deposit = cash(a, "deposits", "50.00");
        long tx = body(deposit).path("transaction").path("transactionId").asLong();
        assertThat(cash(b, "deposits", "10.00").getResponse().getStatus()).isEqualTo(404);
        assertThat(cash(b, "withdrawals", "10.00").getResponse().getStatus()).isEqualTo(404);
        mvc.perform(get("/accounts/{id}/transactions/{tx}", b, tx).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        assertThat(balance(b)).isEqualByComparingTo("0");
    }

    @Test void overflowIsARecordedFailureAndDoesNotPostCash() throws Exception {
        long id = account(token);
        assertThat(cash(id, "deposits", "999999999999999999.99").getResponse().getStatus()).isEqualTo(201);
        var failed = cash(id, "deposits", "0.01");
        assertThat(failed.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(failed).path("transaction").path("failureReason").asText()).isEqualTo("BALANCE_LIMIT_EXCEEDED");
    }

    @Test void balanceFailureRollsBackCashTransactionAndRequestCanBeRetried() throws Exception {
        long id = account(token); UUID key = UUID.randomUUID();
        cash(id, "deposits", "100.00");
        jdbc.execute("ALTER TABLE account_cash_balances ADD CONSTRAINT test_keep_balance CHECK (cash_balance >= 100) NOT VALID");
        try {
            assertThat(cash(id, "withdrawals", "10.00", key, token).getResponse().getStatus()).isEqualTo(500);
            assertThat(balance(id)).isEqualByComparingTo("100.00");
            assertThat(count("transactions", id)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE account_cash_balances DROP CONSTRAINT test_keep_balance"); }
        assertThat(cash(id, "withdrawals", "10.00", key, token).getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(id)).isEqualByComparingTo("90.00");
    }

    @Test void transactionHistoryIsNewestFirstAndPaginated() throws Exception {
        long id = account(token);
        long first = body(cash(id, "deposits", "10.00")).path("transaction").path("transactionId").asLong();
        long second = body(cash(id, "deposits", "20.00")).path("transaction").path("transactionId").asLong();
        var page = getJson("/accounts/" + id + "/transactions?size=1", token);
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("content").get(0).path("transactionId").asLong()).isEqualTo(second);
        assertThat(getJson("/accounts/" + id + "/transactions?page=1&size=1", token)
                .path("content").get(0).path("transactionId").asLong()).isEqualTo(first);
    }
}
