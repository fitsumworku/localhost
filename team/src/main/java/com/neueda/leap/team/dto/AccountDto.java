package com.neueda.leap.team.dto;

import java.time.Instant;

public record AccountDto(long accountId, long userId, String currency, Instant createdDate,
        Instant updatedDate, AccountBalanceDto balance) {}
