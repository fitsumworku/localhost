package com.neueda.leap.team.market;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * About a year of daily closes per symbol, fetched at most once per UTC day (one quota unit each) and shared
 * by every chart. Closes never change once published, so a day-old fetch only lacks the newest candle.
 */
@Component
public class DailyCloseCache {
    // A little over a year, so every range has a baseline close before its first day
    private static final int LOOKBACK_DAYS = 375;
    private static final Duration FAILURE_RETRY = Duration.ofMinutes(5);

    private record Cached(Instant fetchedAt, LocalDate day, NavigableMap<LocalDate, BigDecimal> closes) {}

    private final MarketDataClient market;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public DailyCloseCache(MarketDataClient market, Clock clock) { this.market = market; this.clock = clock; }

    public LocalDate today() { return LocalDate.now(clock.withZone(ZoneOffset.UTC)); }

    /** Closes by date, or null when the provider has no history right now (retried after a few minutes). */
    public NavigableMap<LocalDate, BigDecimal> closes(String ticker) {
        Instant now = clock.instant();
        LocalDate today = today();
        Cached cached = cache.get(ticker);
        boolean fresh = cached != null && (cached.closes() != null
                ? cached.day().equals(today)
                : Duration.between(cached.fetchedAt(), now).compareTo(FAILURE_RETRY) < 0);
        if (fresh) return cached.closes();

        NavigableMap<LocalDate, BigDecimal> closes = market.dailyCloses(ticker, today.minusDays(LOOKBACK_DAYS), today)
                .map(list -> {
                    NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
                    for (DailyClose close : list) map.put(close.date(), close.close());
                    return Collections.unmodifiableNavigableMap(map);
                })
                .orElse(null);
        cache.put(ticker, new Cached(now, today, closes));
        return closes;
    }
}
