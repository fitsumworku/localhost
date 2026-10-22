package com.neueda.leap.team.dto;

import com.neueda.leap.team.entity.enums.DisputeStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin queue row: the dispute plus its owning client, assigned admin and disputed USD amount. */
public record AdminDisputeDto(
        Long disputeId,
        Long accountId,
        Long clientId,
        String clientName,
        String clientEmail,
        Long adminId,
        String adminName,
        Long transactionId,
        Long tradeId,
        String disputeType,
        String description,
        BigDecimal disputedAmount,
        DisputeStatus status,
        Instant dateCreated,
        Instant dateResolved,
        List<DisputeResponseDto> responses
) {}
