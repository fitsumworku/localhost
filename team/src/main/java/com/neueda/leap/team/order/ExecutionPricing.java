package com.neueda.leap.team.order;

import com.neueda.leap.team.config.*;
import com.neueda.leap.team.market.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPricing {
    private final FauxnanceProperties properties;
    private final OrderProperties orders;
    private final TradingCalendar calendar;
    public ExecutionPricing(FauxnanceProperties properties,OrderProperties orders,TradingCalendar calendar) {
        this.properties=properties;this.orders=orders;this.calendar=calendar;
    }
    record Price(MarketQuote quote, BigDecimal quotePrice, BigDecimal fxRate, MarketQuote fxQuote, BigDecimal usdPrice) {}
    record Decision(String kind, String reason, long retrySeconds, Price price) {}
    public static String fxSymbol(String currency) {
        if(currency.equals("USD"))return null;
        return Set.of("EUR","GBP","AUD","NZD").contains(currency)?"FX:"+currency+"USD":"FX:USD"+currency;
    }
    public Set<String> symbols(SecurityDto s) {
        var symbols=new LinkedHashSet<String>();symbols.add(s.ticker());
        String fx=fxSymbol(s.quoteCurrency());if(fx!=null)symbols.add(fx);return symbols;
    }
    Decision evaluate(SecurityDto s,Map<String,QuoteResult> quotes,Instant now) {
        if(!s.status().equals("ACTIVE"))return reject("SECURITY_NOT_TRADABLE");
        QuoteResult result=quotes.get(s.ticker());
        if(result==null||result.quote()==null) {
            if(result!=null&&"SYMBOL_NOT_FOUND".equals(result.errorCode()))return reject("SYMBOL_NOT_FOUND");
            return retry(result==null?"MARKET_DATA_UNAVAILABLE":result.errorCode(),result==null?0:result.retryAfterSeconds());
        }
        MarketQuote quote=result.quote();
        if(!s.ticker().equals(quote.symbol())||!s.quoteCurrency().equals(quote.currency()))return retry("QUOTE_IDENTITY_MISMATCH",0);
        if(!calendar.isOpen(s,quote.marketState(),now))return new Decision("WAIT","MARKET_CLOSED",orders.closedRetrySeconds(),null);
        String problem=invalid(quote,now,s.assetType().equals("FOREX"));
        if(problem!=null)return retry(problem,0);
        MarketQuote fx=null;
        BigDecimal rate=BigDecimal.ONE.setScale(12);
        String fxSymbol=fxSymbol(s.quoteCurrency());
        if(fxSymbol!=null) {
            QuoteResult fxResult=quotes.get(fxSymbol);
            if(fxResult==null||fxResult.quote()==null)return retry("FX_QUOTE_UNAVAILABLE",fxResult==null?0:fxResult.retryAfterSeconds());
            fx=fxResult.quote();
            boolean direct=fxSymbol.endsWith("USD");
            if(!fx.symbol().equals(fxSymbol)||!fx.currency().equals(direct?"USD":s.quoteCurrency()))return retry("FX_IDENTITY_MISMATCH",0);
            problem=invalid(fx,now,true);if(problem!=null)return retry("FX_"+problem,0);
            rate=direct?fx.price().setScale(12,RoundingMode.HALF_UP):BigDecimal.ONE.divide(fx.price(),12,RoundingMode.HALF_UP);
        }
        BigDecimal quotePrice=quote.price().setScale(12,RoundingMode.HALF_UP);
        BigDecimal usd=quotePrice.multiply(rate).setScale(12,RoundingMode.HALF_UP);
        if(!fits(quotePrice)||!fits(rate)||!fits(usd))return reject("PRICE_OUT_OF_RANGE");
        return new Decision("READY",null,0,new Price(quote,quotePrice,rate,fx,usd));
    }
    private String invalid(MarketQuote q,Instant now,boolean fx) {
        if(q.price()==null||q.price().signum()<=0||q.source()==null||q.source().isBlank()||q.source().length()>100)return "INVALID_QUOTE";
        // if(q.stale())return "STALE_QUOTE";
        if(!properties.allowSynthetic()&&q.source().toLowerCase(Locale.ROOT).contains("synthetic"))return "SYNTHETIC_QUOTE_DISABLED";
        if(q.asOf()==null||q.asOf().isAfter(now))return "INVALID_QUOTE_TIME";
        if(q.asOf().isBefore(now.minusSeconds(fx?properties.maxFxAgeSeconds():properties.maxQuoteAgeSeconds())))return "QUOTE_TOO_OLD";
        return null;
    }
    private Decision retry(String reason,long delay) {
        return new Decision("RETRY",reason,Math.min(86400,Math.max(orders.errorRetrySeconds(),delay)),null);
    }
    private static Decision reject(String reason) { return new Decision("REJECT",reason,0,null); }
    private static boolean fits(BigDecimal n) { return n.signum()>0&&n.compareTo(new BigDecimal("10000000000000000"))<0; }
}
