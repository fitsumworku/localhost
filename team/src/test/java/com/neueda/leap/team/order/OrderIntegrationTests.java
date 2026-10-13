package com.neueda.leap.team.order;

import com.neueda.leap.team.support.AccountTestSupport;
import com.neueda.leap.team.market.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(OrderIntegrationTests.QuotesConfig.class)
class OrderIntegrationTests extends AccountTestSupport {
    @Autowired OrderWorker worker;
    @Autowired OrderExecutionService execution;
    @Autowired FakeQuotes quotes;
    long account,security;

    @TestConfiguration(proxyBeanMethods=false)
    static class QuotesConfig { @Bean @Primary FakeQuotes testQuotes(){return new FakeQuotes();} }
    static class FakeQuotes implements MarketDataClient {
        final Map<String,QuoteResult> overrides=new ConcurrentHashMap<>();
        int calls;
        @Override public Map<String,QuoteResult> quotes(Set<String> symbols) {
            calls++;
            var result=new HashMap<String,QuoteResult>();
            symbols.forEach(s->result.put(s,overrides.getOrDefault(s,quote(s,"100","USD","open",false))));
            return result;
        }
    }
    @BeforeEach void fixture() throws Exception {
        quotes.overrides.clear();quotes.calls=0;
        account=account(token);security=security();cash(account,"deposits","1000");
    }
    static QuoteResult quote(String symbol,String price,String currency,String state,boolean stale) {
        return QuoteResult.ok(new MarketQuote(symbol,new BigDecimal(price),currency,Instant.now().minusSeconds(1),state,"upstream:test",stale));
    }
    MvcResult place(String side,String amount,UUID id,String bearer) throws Exception {
        var request=new HashMap<String,Object>();request.put("clientRequestId",id);request.put("securityId",security);request.put("side",side);
        request.put(side.equals("B")?"requestedAmount":"quantityOrdered",new BigDecimal(amount));
        return mvc.perform(post("/accounts/{id}/orders",account).header("Authorization","Bearer "+bearer)
                .contentType("application/json").content(json.writeValueAsString(request))).andReturn();
    }
    long place(String side,String amount) throws Exception {
        var result=place(side,amount,UUID.randomUUID(),token);assertThat(result.getResponse().getStatus()).isEqualTo(202);
        return body(result).path("details").path("order").path("orderId").asLong();
    }
    String orderStatus(long id){return jdbc.queryForObject("SELECT order_status FROM orders WHERE order_id=?",String.class,id);}
    String hold(long id){return jdbc.queryForObject("SELECT status FROM order_reservations WHERE order_id=?",String.class,id);}
    void due(long id){jdbc.update("UPDATE orders SET next_attempt_at=? WHERE order_id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);}
    void holdings(String quantity,String average){jdbc.update("INSERT INTO account_positions(account_id,security_id,quantity,average_price) VALUES (?,?,?,?)",account,security,new BigDecimal(quantity),new BigDecimal(average));}
    BigDecimal number(String column){return jdbc.queryForObject("SELECT "+column+" FROM account_positions WHERE account_id=? AND security_id=?",BigDecimal.class,account,security);}
    long attempts(long id){return jdbc.queryForObject("SELECT count(*) FROM executions WHERE order_id=?",Long.class,id);}

    @Test void acceptanceCommitsReservationBeforeIndependentExecution() throws Exception {
        long id=place("B","250");assertThat(orderStatus(id)).isEqualTo("ACCEPTED");assertThat(hold(id)).isEqualTo("ACTIVE");
        assertThat(balance(account)).isEqualByComparingTo("1000");assertThat(count("trades",account)).isZero();assertThat(quotes.calls).isZero();
        var b=getJson("/accounts/"+account+"/balance",token);assertThat(b.path("availableCash").decimalValue()).isEqualByComparingTo("750");
        assertThat(cash(account,"withdrawals","800").getResponse().getStatus()).isEqualTo(409);
        assertThat(place("B","800",UUID.randomUUID(),token).getResponse().getStatus()).isEqualTo(409);
    }
    @Test void buySettlesExactlyOnceAndUpdatesFinancialState() throws Exception {
        long id=place("B","250");worker.runOnce();worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("FILLED");assertThat(hold(id)).isEqualTo("CONSUMED");
        assertThat(balance(account)).isEqualByComparingTo("750");assertThat(number("quantity")).isEqualByComparingTo("2.5");
        assertThat(number("average_price")).isEqualByComparingTo("100");assertThat(count("trades",account)).isEqualTo(1);assertThat(attempts(id)).isEqualTo(1);
        assertThat(count("transactions",account)).isEqualTo(1);
        assertThat(getJson("/accounts/"+account+"/trades",token).path("totalElements").asLong()).isEqualTo(1);
    }
    @Test void duplicateRequestReturnsSameOrderAndChangedPayloadConflicts() throws Exception {
        UUID key=UUID.randomUUID();var first=body(place("B","250",key,token));
        var second=place("B","250.00",key,token);assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(second).path("replayed").asBoolean()).isTrue();
        assertThat(body(second).path("details").path("order").path("orderId")).isEqualTo(first.path("details").path("order").path("orderId"));
        assertThat(place("B","251",key,token).getResponse().getStatus()).isEqualTo(409);
        worker.runOnce();assertThat(place("B","250",key,token).getResponse().getStatus()).isEqualTo(200);
        assertThat(count("orders",account)).isEqualTo(1);assertThat(count("trades",account)).isEqualTo(1);
    }
    @Test void validationRejectsUnsupportedAndAmbiguousRequests() throws Exception {
        for(String extra:List.of(",\"quantityOrdered\":1",",\"orderType\":\"LIMIT\"",",\"userId\":1")) {
            mvc.perform(post("/accounts/{id}/orders",account).header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"clientRequestId\":\""+UUID.randomUUID()+"\",\"securityId\":"+security+",\"side\":\"B\",\"requestedAmount\":10"+extra+"}"))
                .andExpect(status().isBadRequest());
        }
        assertThat(place("B","0",UUID.randomUUID(),token).getResponse().getStatus()).isEqualTo(400);
        assertThat(place("B","1.001",UUID.randomUUID(),token).getResponse().getStatus()).isEqualTo(400);
        assertThat(count("orders",account)).isZero();
    }
    @Test void ownershipAndAdminReadOnlyApplyToAllOrderRoutes() throws Exception {
        assertThat(place("B","10",UUID.randomUUID(),otherToken).getResponse().getStatus()).isEqualTo(404);
        assertThat(place("B","10",UUID.randomUUID(),adminToken).getResponse().getStatus()).isEqualTo(403);
        long id=place("B","10");String path="/accounts/"+account+"/orders/"+id;
        mvc.perform(get(path).header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
        mvc.perform(get(path+"/executions").header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
        mvc.perform(post(path+"/cancel").header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
        mvc.perform(post(path+"/cancel").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
        assertThat(getJson(path,adminToken).path("order").path("orderId").asLong()).isEqualTo(id);
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path+"/execute").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void sameUsersOtherAccountCannotReadOrCancelAnOrder() throws Exception {
        long id=place("B","10");long second=account(token);
        mvc.perform(get("/accounts/{a}/orders/{o}",second,id).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(post("/accounts/{a}/orders/{o}/cancel",second,id).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void closedMarketKeepsHoldThenFillsAtNewOpeningPrice() throws Exception {
        quotes.overrides.put("TEST",quote("TEST","100","USD","closed",false));
        long id=place("B","250");worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");assertThat(hold(id)).isEqualTo("ACTIVE");assertThat(attempts(id)).isZero();
        assertThat(getJson("/accounts/"+account+"/orders/"+id,token).path("lastExecutionMessage").asText()).isEqualTo("MARKET_CLOSED");
        int calls=quotes.calls;worker.runOnce();assertThat(quotes.calls).isEqualTo(calls);
        quotes.overrides.put("TEST",quote("TEST","125","USD","open",false));due(id);worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("FILLED");assertThat(number("quantity")).isEqualByComparingTo("2");assertThat(balance(account)).isEqualByComparingTo("750");
    }
    @Test void cancellationIsIdempotentAndReleasesCash() throws Exception {
        long id=place("B","900");String path="/accounts/"+account+"/orders/"+id+"/cancel";
        mvc.perform(post(path).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post(path).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        assertThat(orderStatus(id)).isEqualTo("CANCELLED");assertThat(hold(id)).isEqualTo("RELEASED");worker.runOnce();
        assertThat(cash(account,"withdrawals","1000").getResponse().getStatus()).isEqualTo(201);
    }
    @Test void sellHoldsPreventOversellingAndPartialPositionKeepsItsAverage() throws Exception {
        holdings("5","80");long id=place("S","3");
        assertThat(place("S","3",UUID.randomUUID(),token).getResponse().getStatus()).isEqualTo(409);
        worker.runOnce();assertThat(orderStatus(id)).isEqualTo("FILLED");assertThat(number("quantity")).isEqualByComparingTo("2");
        assertThat(number("average_price")).isEqualByComparingTo("80");assertThat(balance(account)).isEqualByComparingTo("1300");
        place("S","2");worker.runOnce();assertThat(number("quantity")).isZero();assertThat(number("average_price")).isZero();
    }
    @Test void secondBuyCalculatesWeightedAverageFromPostedCost() throws Exception {
        holdings("5","80");place("B","500");worker.runOnce();
        assertThat(number("quantity")).isEqualByComparingTo("10");assertThat(number("average_price")).isEqualByComparingTo("90");
    }
    @Test void buyRoundingNeverSpendsBeyondTheReservation() throws Exception {
        quotes.overrides.put("TEST",quote("TEST","3","USD","open",false));long id=place("B","100");worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("FILLED");assertThat(number("quantity")).isEqualByComparingTo("33.333333333333");
        assertThat(balance(account)).isEqualByComparingTo("900");
    }
    @Test void tinySellThatRoundsToZeroIsRejectedWithoutLosingHoldings() throws Exception {
        holdings("1","100");long id=place("S","0.000000000001");worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("REJECTED");assertThat(hold(id)).isEqualTo("RELEASED");assertThat(number("quantity")).isEqualByComparingTo("1");
        assertThat(count("trades",account)).isZero();
    }
    @Test void haltedSecurityIsRejectedAtSubmissionAndRecheckedBeforeFill() throws Exception {
        jdbc.update("UPDATE securities SET status='HALTED' WHERE security_id=?",security);
        var denied=place("B","20",UUID.randomUUID(),token);assertThat(denied.getResponse().getStatus()).isEqualTo(409);
        assertThat(count("order_reservations",account)).isZero();
        jdbc.update("UPDATE securities SET status='ACTIVE' WHERE security_id=?",security);long id=place("B","20");
        jdbc.update("UPDATE securities SET status='HALTED' WHERE security_id=?",security);worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("REJECTED");assertThat(hold(id)).isEqualTo("RELEASED");
    }
    @Test void quoteFailuresRetryWithoutReleasingReservationAndCanRecover() throws Exception {
        long id=place("B","20");quotes.overrides.put("TEST",QuoteResult.error("RATE_LIMITED",3600));worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");assertThat(hold(id)).isEqualTo("ACTIVE");assertThat(attempts(id)).isEqualTo(1);
        var detail=getJson("/accounts/"+account+"/orders/"+id,token);
        assertThat(Instant.parse(detail.path("nextAttemptAt").asText())).isAfter(Instant.now().plusSeconds(3500));
        quotes.overrides.clear();due(id);worker.runOnce();assertThat(orderStatus(id)).isEqualTo("FILLED");assertThat(attempts(id)).isEqualTo(2);
    }
    @Test void staleQuoteAndCurrencyMismatchCannotFill() throws Exception {
        long id=place("B","20");quotes.overrides.put("TEST",quote("TEST","100","USD","open",true));worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");assertThat(count("trades",account)).isZero();
        quotes.overrides.put("TEST",quote("TEST","100","EUR","open",false));due(id);worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("ACCEPTED");assertThat(hold(id)).isEqualTo("ACTIVE");assertThat(attempts(id)).isEqualTo(2);
    }
    @Test void permanentlyUnknownSymbolReleasesReservation() throws Exception {
        long id=place("B","20");quotes.overrides.put("TEST",QuoteResult.error("SYMBOL_NOT_FOUND",0));worker.runOnce();
        assertThat(orderStatus(id)).isEqualTo("REJECTED");assertThat(hold(id)).isEqualTo("RELEASED");
    }
    @Test void nonUsdQuotePersistsActualConversionAndUsdCash() throws Exception {
        jdbc.update("UPDATE securities SET quote_currency='INR' WHERE security_id=?",security);
        quotes.overrides.put("TEST",quote("TEST","170","INR","open",false));
        quotes.overrides.put("FX:USDINR",quote("FX:USDINR","85","INR","closed",false));
        long id=place("B","100");worker.runOnce();assertThat(orderStatus(id)).isEqualTo("FILLED");
        assertThat(balance(account)).isEqualByComparingTo("900");
        var fill=getJson("/accounts/"+account+"/orders/"+id+"/executions",token).path("content").get(0);
        assertThat(fill.path("quoteCurrency").asText()).isEqualTo("INR");assertThat(fill.path("quotePrice").decimalValue()).isEqualByComparingTo("170");
        assertThat(fill.path("fxRateToUsd").decimalValue()).isEqualByComparingTo("0.011764705882");assertThat(fill.path("fxSource").asText()).isEqualTo("upstream:test");
    }
    @Test void missingFxNeverTreatsForeignPriceAsUsd() throws Exception {
        jdbc.update("UPDATE securities SET quote_currency='INR' WHERE security_id=?",security);
        quotes.overrides.put("TEST",quote("TEST","170","INR","open",false));quotes.overrides.put("FX:USDINR",QuoteResult.error("BACKFILL_IN_PROGRESS",60));
        long id=place("B","100");worker.runOnce();assertThat(orderStatus(id)).isEqualTo("ACCEPTED");assertThat(balance(account)).isEqualByComparingTo("1000");
    }
    @Test void activeExecutionBlocksCancelAndRepeatedCompletionIsHarmless() throws Exception {
        long id=place("B","20");var claim=execution.claim(id).orElseThrow();assertThat(execution.claim(id)).isEmpty();
        mvc.perform(post("/accounts/{a}/orders/{o}/cancel",account,id).header("Authorization","Bearer "+token)).andExpect(status().isConflict());
        var values=quotes.quotes(Set.of("TEST"));execution.complete(claim,values);execution.complete(claim,values);
        assertThat(orderStatus(id)).isEqualTo("FILLED");assertThat(count("trades",account)).isEqualTo(1);
    }
    @Test void expiredLeaseIsRecoveredAndOldWorkerCannotPost() throws Exception {
        long id=place("B","20");var old=execution.claim(id).orElseThrow();
        jdbc.update("UPDATE orders SET execution_lease_until=? WHERE order_id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);
        var recovered=execution.claim(id).orElseThrow();assertThat(old.token()).isNotEqualTo(recovered.token());
        execution.complete(old,quotes.quotes(Set.of("TEST")));assertThat(orderStatus(id)).isEqualTo("IN_EXECUTION");assertThat(count("trades",account)).isZero();
        execution.complete(recovered,quotes.quotes(Set.of("TEST")));assertThat(orderStatus(id)).isEqualTo("FILLED");assertThat(attempts(id)).isEqualTo(2);
    }
    @Test void settlementFailureRollsBackExecutionTradeCashAndPosition() throws Exception {
        long id=place("B","20");var claim=execution.claim(id).orElseThrow();
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT test_reject_fill CHECK(order_status<>'FILLED') NOT VALID");
        try {assertThatThrownBy(()->execution.complete(claim,quotes.quotes(Set.of("TEST")))).isInstanceOf(RuntimeException.class);}
        finally {jdbc.execute("ALTER TABLE orders DROP CONSTRAINT test_reject_fill");}
        assertThat(balance(account)).isEqualByComparingTo("1000");assertThat(count("trades",account)).isZero();assertThat(count("account_positions",account)).isZero();
        assertThat(attempts(id)).isZero();assertThat(hold(id)).isEqualTo("ACTIVE");assertThat(orderStatus(id)).isEqualTo("IN_EXECUTION");
        execution.complete(claim,quotes.quotes(Set.of("TEST")));assertThat(orderStatus(id)).isEqualTo("FILLED");
    }
    @Test void acceptedCommitmentSurvivesOwnerSuspensionButNewRequestsAreBlocked() throws Exception {
        long id=place("B","20");jdbc.update("UPDATE users SET status='SUSPENDED',token_version=token_version+1 WHERE user_id=?",owner);
        assertThat(place("B","20",UUID.randomUUID(),token).getResponse().getStatus()).isEqualTo(401);
        worker.runOnce();assertThat(orderStatus(id)).isEqualTo("FILLED");
    }
    @Test void securitiesSearchAndIndicativeQuoteAreAuthenticated() throws Exception {
        var securities=getJson("/securities?q=TEST",token);assertThat(securities.path("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson("/securities/"+security+"/quote",token).path("symbol").asText()).isEqualTo("TEST");
        mvc.perform(get("/securities")).andExpect(status().isUnauthorized());
    }
    @Test void nativePostgresSerializesCompetingBuyReservations() throws Exception {
        Assumptions.assumeTrue(nativePostgres(),"Requires independently isolated PostgreSQL sessions");
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await();return place("B","800",UUID.randomUUID(),token).getResponse().getStatus();});
            var b=pool.submit(()->{barrier.await();return place("B","800",UUID.randomUUID(),token).getResponse().getStatus();});
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(202,409);
            assertThat(jdbc.queryForObject("SELECT sum(reserved_cash) FROM order_reservations WHERE account_id=? AND status='ACTIVE'",BigDecimal.class,account)).isEqualByComparingTo("800");
        } finally {pool.shutdownNow();}
    }
    @Test void nativePostgresDuplicateSubmissionsProduceOneReservation() throws Exception {
        Assumptions.assumeTrue(nativePostgres(),"Requires independently isolated PostgreSQL sessions");
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);UUID request=UUID.randomUUID();
        try {
            var a=pool.submit(()->{barrier.await();return place("B","200",request,token).getResponse().getStatus();});
            var b=pool.submit(()->{barrier.await();return place("B","200",request,token).getResponse().getStatus();});
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(202,200);
            assertThat(count("orders",account)).isEqualTo(1);assertThat(count("order_reservations",account)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
}
