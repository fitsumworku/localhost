package com.neueda.leap.team.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * End-of-day account value in USD (cash + holdings at that day's close), oldest first, up to yesterday.
 * complete is false when some holding had no price history and was valued at its last execution price.
 */
public record ValueHistoryDto(long accountId, String range, List<Point> points, boolean complete) {
    public ValueHistoryDto { points = List.copyOf(points); }
    public record Point(LocalDate date, BigDecimal value) {}
}
