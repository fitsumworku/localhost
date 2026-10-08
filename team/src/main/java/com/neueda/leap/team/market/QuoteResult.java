package com.neueda.leap.team.market;

public record QuoteResult(MarketQuote quote, String errorCode, long retryAfterSeconds) {
    public static QuoteResult ok(MarketQuote quote) { return new QuoteResult(quote, null, 0); }
    public static QuoteResult error(String code, long retry) { return new QuoteResult(null, code, retry); }
}
