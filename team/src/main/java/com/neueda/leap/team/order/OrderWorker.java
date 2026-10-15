package com.neueda.leap.team.order;

import com.neueda.leap.team.config.OrderProperties;
import com.neueda.leap.team.market.*;
import java.time.Clock;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class OrderWorker {
    private static final Logger log=LoggerFactory.getLogger(OrderWorker.class);
    private final OrderRepository repo;
    private final OrderExecutionService execution;
    private final ExecutionPricing pricing;
    private final MarketDataClient market;
    private final OrderProperties properties;
    private final Clock clock;
    public OrderWorker(OrderRepository repo,OrderExecutionService execution,ExecutionPricing pricing,
            MarketDataClient market,OrderProperties properties,Clock clock) {
        this.repo=repo;this.execution=execution;this.pricing=pricing;this.market=market;this.properties=properties;this.clock=clock;
    }
    public void runOnce() {
        var claims=new ArrayList<OrderExecutionService.Claim>();
        for(long id:repo.due(clock.instant(),properties.batchSize())) {
            try { execution.claim(id).ifPresent(claims::add); }
            catch(RuntimeException e) { log.error("Could not claim order {} ({})",id,e.getClass().getSimpleName()); }
        }
        if(claims.isEmpty())return;
        var symbols=new LinkedHashSet<String>();claims.forEach(c->symbols.addAll(pricing.symbols(c.security())));
        Map<String,QuoteResult> quotes;
        try { quotes=market.quotes(symbols); }
        catch(RuntimeException e) {
            quotes=new HashMap<>();for(String symbol:symbols)quotes.put(symbol,QuoteResult.error("MARKET_DATA_UNAVAILABLE",60));
        }
        for(var claim:claims) {
            try { execution.complete(claim,quotes); }
            // Failed DB transactions roll back completely. Their leases expire and become retryable.
            catch(RuntimeException e) { log.error("Could not complete order {} ({})",claim.orderId(),e.getClass().getSimpleName()); }
        }
    }
}
