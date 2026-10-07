package com.neueda.leap.team.dto;

import com.neueda.leap.team.entity.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashTransactionDto(long transactionId, long accountId, UUID clientRequestId,
        BigDecimal amount, CashTransactionType type, CashTransactionStatus status,
        Instant transactionDate, Instant completedAt, String failureReason,
        Long ledgerId, BigDecimal balanceAfter) {}
