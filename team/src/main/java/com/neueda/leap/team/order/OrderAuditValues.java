package com.neueda.leap.team.order;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit audit allowlist: never serialize a worker claim or its execution token. */
final class OrderAuditValues {
    private OrderAuditValues() {}

    static Map<String, Object> snapshot(OrderRow order, String reservationStatus) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("accountId", order.accountId());
        values.put("securityId", order.securityId());
        values.put("side", order.side());
        values.put("requestedAmount", order.amount());
        values.put("quantityOrdered", order.quantity());
        values.put("orderStatus", order.status());
        values.put("createdDate", order.createdAt());
        values.put("updatedDate", order.updatedAt());
        values.put("acceptedAt", order.acceptedAt());
        values.put("terminalAt", order.terminalAt());
        values.put("rejectionReason", order.rejectionReason());
        values.put("nextAttemptAt", order.nextAttemptAt());
        values.put("executionAttempts", order.attempts());
        values.put("lastExecutionMessage", order.message());
        values.put("reservationStatus", reservationStatus);
        return values;
    }
}
