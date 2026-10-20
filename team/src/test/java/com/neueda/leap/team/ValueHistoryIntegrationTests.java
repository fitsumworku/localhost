package com.neueda.leap.team;

import com.neueda.leap.team.market.*;
import com.neueda.leap.team.support.AccountTestSupport;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(ValueHistoryIntegrationTests.ClosesConfig.class)
class ValueHistoryIntegrationTests extends AccountTestSupport {
    static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @TestConfiguration(proxyBeanMethods = false)
    static class ClosesConfig { @Bean @Primary FakeCloses testCloses() { return new FakeCloses(); } }
    static class FakeCloses implements MarketDataClient {
        @Override public Map<String, QuoteResult> quotes(Set<String> symbols) { return Map.of(); }
        @Override public Optional<List<DailyClose>> dailyCloses(String symbol, LocalDate from, LocalDate to) {
            return Optional.of(List.of(new DailyClose(TODAY.minusDays(5), new BigDecimal("20")),
                    new DailyClose(TODAY.minusDays(2), new BigDecimal("30"))));
        }
    }

    @Test void rewindsCashAndPricesHoldingsAtEachDaysClose() throws Exception {
        long id = account(token);
        cash(id, "deposits", "100.00");
        jdbc.update("UPDATE accounts SET created_date = now() - interval '10 days' WHERE account_id = ?", id);
        jdbc.update("UPDATE transactions SET transaction_date = now() - interval '3 days', completed_at = now() - interval '3 days' WHERE account_id = ?", id);
        cash(id, "deposits", "50.00");
        jdbc.update("INSERT INTO account_positions(account_id, security_id, quantity, average_price) VALUES (?, ?, 2, 10)", id, security());

        var week = getJson("/accounts/" + id + "/value-history?range=1W", token);
        var values = new ArrayList<BigDecimal>();
        week.path("points").forEach(p -> values.add(p.path("value").decimalValue()));
        // Before the first close the holding falls back to its average price (2 x 10), so the result is incomplete
        assertThat(values).usingElementComparator(BigDecimal::compareTo).containsExactly(
                new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("40"), new BigDecimal("40"),
                new BigDecimal("140"), new BigDecimal("160"), new BigDecimal("160"));
        assertThat(week.path("points").get(0).path("date").asText()).isEqualTo(TODAY.minusWeeks(1).toString());
        assertThat(week.path("points").get(6).path("date").asText()).isEqualTo(TODAY.minusDays(1).toString());
        assertThat(week.path("complete").asBoolean()).isFalse();

        var day = getJson("/accounts/" + id + "/value-history?range=1D", token).path("points");
        assertThat(day).hasSize(1);
        assertThat(day.get(0).path("value").decimalValue()).isEqualByComparingTo("160");
    }

    @Test void newAccountHasNoHistoryYet() throws Exception {
        long id = account(token);
        var history = getJson("/accounts/" + id + "/value-history?range=1Y", token);
        assertThat(history.path("points")).isEmpty();
        assertThat(history.path("complete").asBoolean()).isTrue();
    }

    @Test void securityClosesStartAtTheLastCloseBeforeTheRange() throws Exception {
        long id = security();
        // 1D begins today, so its only close is the previous close (two days ago in the fake data)
        var day = getJson("/securities/" + id + "/closes?range=1D", token);
        assertThat(day.path("ticker").asText()).isEqualTo("TEST");
        assertThat(day.path("currency").asText()).isEqualTo("USD");
        assertThat(day.path("closes")).hasSize(1);
        assertThat(day.path("closes").get(0).path("close").decimalValue()).isEqualByComparingTo("30");

        var week = getJson("/securities/" + id + "/closes?range=1W", token).path("closes");
        assertThat(week).hasSize(2);
        assertThat(week.get(0).path("date").asText()).isEqualTo(TODAY.minusDays(5).toString());

        mvc.perform(get("/securities/{id}/closes", id).param("range", "5Y").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectsUnknownRangeAndOtherClientsAccounts() throws Exception {
        long id = account(token);
        mvc.perform(get("/accounts/{id}/value-history", id).param("range", "5Y").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/accounts/{id}/value-history", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}
