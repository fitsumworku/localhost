package com.neueda.leap.team.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** USD volume is gross executed notional (buy + sell), not cash flow or profit. */
public final class ReportingDtos {
    private ReportingDtos() {}

    public record Freshness(Instant sourceSnapshotAt, Instant lastSuccessfulAt) {}
    public record Report<T>(ReportingFilter window, Freshness freshness, T data) {}
    public record VolumeTotals(BigDecimal grossVolumeUsd, BigDecimal buyVolumeUsd,
                               BigDecimal sellVolumeUsd, long tradeCount,
                               long buyTradeCount, long sellTradeCount) {}
    public record SectorVolume(String sector, VolumeTotals volume) {}
    public record SecurityVolume(long securityId, String ticker, String name,
                                 String exchange, String assetType, String sector,
                                 VolumeTotals volume, BigDecimal quantityTraded,
                                 BigDecimal buyQuantity, BigDecimal sellQuantity) {}
    public record UserVolume(long userId, String name, VolumeTotals volume) {}
    public record SideVolume(String side, BigDecimal volumeUsd, long tradeCount) {}
    public record OrderMetrics(long submittedTotal, Map<String, Long> currentStatusCounts) {}

    /** Population counts are current across all time; only tradingClientsInPeriod uses the filter. */
    public record UserMetrics(long totalClients, long activeClients, long blacklistedClients,
                              long totalAdmins, long tradingClientsInPeriod) {}
    public record Summary(VolumeTotals volume, OrderMetrics orders, UserMetrics users) {}
}
