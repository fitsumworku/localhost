package com.neueda.leap.team.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountBalanceDto(long accountId, String currency, BigDecimal cashBalance,
        BigDecimal reservedCash, BigDecimal availableCash, Instant updatedDate) {}
