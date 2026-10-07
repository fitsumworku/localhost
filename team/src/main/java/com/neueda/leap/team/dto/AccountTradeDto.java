package com.neueda.leap.team.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTradeDto(long tradeId, long accountId, long orderId, long executionId,
        long securityId, String ticker, String name, String side, BigDecimal quantityFilled,
        BigDecimal priceOfExecution, BigDecimal cashAmount, Instant tradeDate, Instant settlementDate,
        BigDecimal quotePrice, String quoteCurrency, Instant quoteTimestamp, String quoteSource,
        BigDecimal fxRateToUsd, Instant fxQuoteTimestamp, String fxSource) {}
