package com.neueda.leap.team.order;

import java.math.BigDecimal;
import java.time.Instant;

public record ExecutionDto(long executionId, long orderId, String status,
        Instant startedAt, Instant finishedAt, BigDecimal quantityFilled,
        BigDecimal priceOfExecution, BigDecimal cashAmount, BigDecimal quotePrice,
        String quoteCurrency, Instant quoteTimestamp, String quoteSource,
        BigDecimal fxRateToUsd, Instant fxQuoteTimestamp, String fxSource, String failureReason) {}
