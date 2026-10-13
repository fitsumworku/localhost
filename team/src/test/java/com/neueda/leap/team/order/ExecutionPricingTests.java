package com.neueda.leap.team.order;

import com.neueda.leap.team.config.*;
import com.neueda.leap.team.market.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExecutionPricingTests {
    final Instant now=Instant.parse("2026-10-07T15:00:00Z");
    final TradingCalendar calendar=new TradingCalendar(new TradingCalendarProperties(true,List.of(),List.of()));
    final ExecutionPricing pricing=new ExecutionPricing(new FauxnanceProperties("https://example.test","",10,true,1800,345600),new OrderProperties(25,180,300,60),calendar);
    SecurityDto security(String type,String exchange,String symbol,String currency){return new SecurityDto(1,symbol,"test",type,exchange,currency,type.equals("FOREX")?"EUR":null,"ACTIVE");}
    MarketQuote quote(String symbol,String currency,Instant at,String state){return new MarketQuote(symbol,new BigDecimal("100"),currency,at,state,"synthetic",false);}
    @Test void usSessionsObserveDstAndWeekends(){
        var s=security("EQUITY","US","TEST","USD");
        assertThat(calendar.isOpen(s,"unknown",Instant.parse("2026-07-06T13:29:59Z"))).isFalse();
        assertThat(calendar.isOpen(s,"unknown",Instant.parse("2026-07-06T13:30:00Z"))).isTrue();
        assertThat(calendar.isOpen(s,"open",Instant.parse("2026-07-06T20:00:00Z"))).isFalse();
        assertThat(calendar.isOpen(s,"open",Instant.parse("2026-01-05T14:30:00Z"))).isTrue();
        assertThat(calendar.isOpen(s,"open",Instant.parse("2026-07-05T15:00:00Z"))).isFalse();
    }
    @Test void indiaSessionsAndExplicitClosures(){
        var s=security("ETF","NSE","TEST.NS","INR");
        assertThat(calendar.isOpen(s,"unknown",Instant.parse("2026-10-07T03:45:00Z"))).isTrue();
        assertThat(calendar.isOpen(s,"open",Instant.parse("2026-10-07T10:00:00Z"))).isFalse();
        var closed=new TradingCalendar(new TradingCalendarProperties(true,List.of(),List.of(LocalDate.parse("2026-10-07"))));
        assertThat(closed.isOpen(s,"unknown",Instant.parse("2026-10-07T04:00:00Z"))).isFalse();
    }
    @Test void knownClosedAndStrictUnknownNeverFillEquities(){
        var s=security("EQUITY","US","TEST","USD");
        assertThat(calendar.isOpen(s,"closed",now)).isFalse();assertThat(calendar.isOpen(s,"post",now)).isFalse();
        var strict=new TradingCalendar(new TradingCalendarProperties(false,List.of(),List.of()));
        assertThat(strict.isOpen(s,"unknown",now)).isFalse();assertThat(strict.isOpen(s,"open",now)).isTrue();
    }
    @Test void forexDailyReferenceClosedFlagDoesNotCloseTheSimulationDuringWeek(){
        var s=security("FOREX","FX","FX:EURUSD","USD");
        assertThat(calendar.isOpen(s,"closed",now)).isTrue();
        assertThat(calendar.isOpen(s,"closed",Instant.parse("2026-10-09T21:00:00Z"))).isFalse();
        assertThat(calendar.isOpen(s,"closed",Instant.parse("2026-10-11T20:59:59Z"))).isFalse();
        assertThat(calendar.isOpen(s,"closed",Instant.parse("2026-10-11T21:00:00Z"))).isTrue();
        var decision=pricing.evaluate(s,Map.of(s.ticker(),QuoteResult.ok(quote(s.ticker(),"USD",now.minusSeconds(86400),"closed"))),now);
        assertThat(decision.kind()).isEqualTo("READY");
    }
    @Test void cryptoTradesOnWeekendsWithFreshUnknownQuote(){
        var s=security("CRYPTO","CRYPTO","X:BTC-USD","USD");Instant weekend=Instant.parse("2026-10-10T12:00:00Z");
        assertThat(pricing.evaluate(s,Map.of(s.ticker(),QuoteResult.ok(quote(s.ticker(),"USD",weekend,"unknown"))),weekend).kind()).isEqualTo("READY");
    }
    @Test void oldAndFutureQuotesRemainQueued(){
        var s=security("CRYPTO","CRYPTO","X:BTC-USD","USD");
        assertThat(pricing.evaluate(s,Map.of(s.ticker(),QuoteResult.ok(quote(s.ticker(),"USD",now.minusSeconds(1900),"open"))),now).reason()).isEqualTo("QUOTE_TOO_OLD");
        assertThat(pricing.evaluate(s,Map.of(s.ticker(),QuoteResult.ok(quote(s.ticker(),"USD",now.plusSeconds(1),"open"))),now).reason()).isEqualTo("INVALID_QUOTE_TIME");
    }
    @Test void fxDirectionsAreExplicit(){
        assertThat(ExecutionPricing.fxSymbol("USD")).isNull();assertThat(ExecutionPricing.fxSymbol("GBP")).isEqualTo("FX:GBPUSD");
        assertThat(ExecutionPricing.fxSymbol("INR")).isEqualTo("FX:USDINR");assertThat(ExecutionPricing.fxSymbol("JPY")).isEqualTo("FX:USDJPY");
    }
}
