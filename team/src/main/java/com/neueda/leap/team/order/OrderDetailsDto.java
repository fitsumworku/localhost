package com.neueda.leap.team.order;

import com.neueda.leap.team.dto.AccountOrderDto;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderDetailsDto(AccountOrderDto order, Reservation reservation,
        Instant nextAttemptAt, int executionAttempts, String lastExecutionMessage) {
    public record Reservation(String status, BigDecimal reservedCash, BigDecimal reservedQuantity,
                              Instant createdAt, Instant resolvedAt) {}
}
