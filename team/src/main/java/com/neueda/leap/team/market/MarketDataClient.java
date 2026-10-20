package com.neueda.leap.team.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface MarketDataClient {
    Map<String, QuoteResult> quotes(Set<String> symbols);

    /** Daily closes in ascending date order, or empty when history is unavailable. */
    default Optional<List<DailyClose>> dailyCloses(String symbol, LocalDate from, LocalDate to) { return Optional.empty(); }
}
