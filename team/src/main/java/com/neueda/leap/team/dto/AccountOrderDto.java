package com.neueda.leap.team.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountOrderDto(long orderId, long accountId, long securityId, String ticker,
        String name, UUID clientRequestId, String side, BigDecimal requestedAmount,
        BigDecimal quantityOrdered, String orderStatus, Instant createdDate, Instant updatedDate,
        Instant acceptedAt, Instant terminalAt, String rejectionReason) {}
