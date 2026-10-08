package com.neueda.leap.team.market;

import java.util.Map;
import java.util.Set;

public interface MarketDataClient {
    Map<String, QuoteResult> quotes(Set<String> symbols);
}
