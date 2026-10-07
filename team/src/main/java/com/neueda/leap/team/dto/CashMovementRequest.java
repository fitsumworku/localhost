package com.neueda.leap.team.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CashMovementRequest(@NotNull UUID clientRequestId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 18, fraction = 2) BigDecimal amount) {}
