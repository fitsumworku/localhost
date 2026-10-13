package com.neueda.leap.team.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(String symbol, BigDecimal price, String currency, Instant asOf,
                          String marketState, String source, boolean stale) {}
