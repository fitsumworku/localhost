package com.neueda.leap.team.reporting;

import com.neueda.leap.team.exception.ApiException;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** Trade reports use execution/trade time; order reports use submission time. */
public record ReportingFilter(Instant fromInclusive, Instant toExclusive, AssetType assetType) {
    private static final Instant EARLIEST = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant LATEST_EXCLUSIVE = Instant.parse("+10000-01-01T00:00:00Z");
    public enum AssetType { EQUITY, ETF, FOREX, CRYPTO }
    public enum Side {
        BUY("B"), SELL("S");
        private final String code;
        Side(String code) { this.code = code; }
        String code() { return code; }
    }

    public ReportingFilter {
        checkRepresentable(fromInclusive);
        checkRepresentable(toExclusive);
        if (fromInclusive != null && toExclusive != null && !fromInclusive.isBefore(toExclusive)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_RANGE", "from must be earlier than to.");
        }
    }

    private static void checkRepresentable(Instant value) {
        if (value != null && (value.isBefore(EARLIEST) || !value.isBefore(LATEST_EXCLUSIVE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_RANGE", "Report timestamps must have a year between 0001 and 9999.");
        }
    }

    static int validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_LIMIT", "limit must be between 1 and 100.");
        }
        return limit;
    }
}
