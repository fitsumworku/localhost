package com.neueda.leap.team.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Internal JDBC projection; never returned from a controller. */
record OrderRow(long id, long accountId, long ownerId, long securityId, UUID clientRequestId,
        String side, BigDecimal amount, BigDecimal quantity, String status,
        Instant createdAt, Instant updatedAt, Instant acceptedAt, Instant terminalAt, String rejectionReason,
        Instant nextAttemptAt, UUID executionToken, Instant leaseUntil, int attempts, String message,
        SecurityDto security) {}
