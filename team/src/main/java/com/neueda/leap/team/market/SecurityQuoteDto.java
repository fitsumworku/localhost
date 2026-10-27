package com.neueda.leap.team.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Indicative quote for display. tradingOpen is the same TradingCalendar decision order execution
 * uses (US equities: weekdays 9:30-16:00 America/New_York), so the UI badge matches when orders fill.
 * marketState is the provider's raw value and is often "unknown" or "closed" regardless of session.
 */
public record SecurityQuoteDto(String symbol, BigDecimal price, String currency, Instant asOf,
                               String marketState, String source, boolean stale, boolean tradingOpen) {
    public static SecurityQuoteDto of(MarketQuote quote, boolean tradingOpen) {
        return new SecurityQuoteDto(quote.symbol(), quote.price(), quote.currency(), quote.asOf(),
                quote.marketState(), quote.source(), quote.stale(), tradingOpen);
    }
}
