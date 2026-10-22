package com.neueda.leap.team.dto;

import com.neueda.leap.team.entity.enums.DisputeStatus;
import java.time.Instant;
import java.util.List;

public record DisputeDto(
        Long disputeId,
        Long accountId,
        Long adminId,
        Long transactionId,
        Long tradeId,
        String disputeType,
        String description,
        DisputeStatus status,
        Instant dateCreated,
        Instant dateResolved,
        List<DisputeResponseDto> responses
) {}