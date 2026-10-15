package com.neueda.leap.team.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Market BUY: requestedAmount (USD). Market SELL: quantityOrdered (instrument units). */
public record PlaceOrderRequest(@NotNull UUID clientRequestId, @Positive long securityId,
        @NotNull Side side,
        @DecimalMin("0.01") @Digits(integer=18, fraction=2) BigDecimal requestedAmount,
        @DecimalMin("0.000000000001") @Digits(integer=16, fraction=12) BigDecimal quantityOrdered) {
    public enum Side { B, S }
}
