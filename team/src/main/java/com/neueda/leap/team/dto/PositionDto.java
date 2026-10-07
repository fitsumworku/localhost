package com.neueda.leap.team.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionDto(long positionId, long accountId, long securityId, String ticker, String name,
        String assetType, String exchange, String quoteCurrency, String accountCurrency,
        BigDecimal quantity, BigDecimal reservedQuantity, BigDecimal availableQuantity,
        BigDecimal averagePrice, Instant updatedDate) {}
