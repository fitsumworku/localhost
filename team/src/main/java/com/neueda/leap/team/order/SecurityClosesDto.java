package com.neueda.leap.team.order;

import com.neueda.leap.team.market.DailyClose;
import java.util.List;

/**
 * Raw daily closes in the security's quote currency, oldest first. The first close is the baseline: the last
 * close before the range starts (for 1D, the previous close), so change over the range is last minus first.
 */
public record SecurityClosesDto(long securityId, String ticker, String currency, String range, List<DailyClose> closes) {
    public SecurityClosesDto { closes = List.copyOf(closes); }
}
