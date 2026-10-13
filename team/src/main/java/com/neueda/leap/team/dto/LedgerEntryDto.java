package com.neueda.leap.team.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryDto(long ledgerId, long accountId, Long transactionId, Long tradeId,
        String entryType, BigDecimal debitAmount, BigDecimal creditAmount,
        BigDecimal runningBalance, Instant entryDate) {}
