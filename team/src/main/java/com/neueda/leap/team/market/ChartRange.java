package com.neueda.leap.team.market;

import com.neueda.leap.team.exception.ApiException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/** The look-back periods offered by the charts. */
public enum ChartRange {
    D1("1D"), W1("1W"), M1("1M"), Y1("1Y");

    private final String code;
    ChartRange(String code) { this.code = code; }

    public String code() { return code; }

    /** First day of the period: 1D starts today, so its baseline is the previous close. */
    public LocalDate start(LocalDate today) {
        return switch (this) {
            case D1 -> today;
            case W1 -> today.minusWeeks(1);
            case M1 -> today.minusMonths(1);
            case Y1 -> today.minusYears(1);
        };
    }

    public static ChartRange parse(String code) {
        for (ChartRange range : values()) if (range.code.equals(code)) return range;
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "range must be one of 1D, 1W, 1M, 1Y.");
    }
}
