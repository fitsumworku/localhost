package com.neueda.leap.team.reporting;

import com.neueda.leap.team.reporting.ReportingDtos.*;
import com.neueda.leap.team.reporting.ReportingFilter.AssetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@ConditionalOnProperty(prefix = "app.reporting", name = "enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reporting", description = "Admin reports from the latest OLAP snapshot. ISO instant ranges are from-inclusive/to-exclusive; omitted bounds mean all time.")
public class ReportingController {
    private final ReportingService reports;
    private final ReportingEtlService etl;

    public ReportingController(ReportingService reports, ReportingEtlService etl) {
        this.reports = reports;
        this.etl = etl;
    }

    @GetMapping("/summary")
    @Operation(summary = "Trading, orders and current client population",
            description = "Gross volume = executed BUY USD notional + SELL USD notional. Orders are selected by submission time; trades by trade time. Population counts always describe all current clients, regardless of range.")
    public Report<Summary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AssetType assetType) {
        return reports.summary(new ReportingFilter(from, to, assetType));
    }

    @GetMapping("/volume/sectors")
    @Operation(summary = "Top sectors by gross USD trading volume", description = "Missing/blank sectors are Unclassified. Returns up to limit rows, sorted by volume descending. limit: 1–100; default 10.")
    public Report<List<SectorVolume>> sectors(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(defaultValue = "10") int limit) {
        return reports.sectors(new ReportingFilter(from, to, assetType), limit);
    }

    @GetMapping("/volume/securities")
    @Operation(summary = "Top securities by gross USD trading volume", description = "Use assetType=EQUITY for stocks. Instruments on different exchanges remain separate. Quantities are summed only within each instrument. limit: 1–100; default 10.")
    public Report<List<SecurityVolume>> securities(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(defaultValue = "10") int limit) {
        return reports.securities(new ReportingFilter(from, to, assetType), limit);
    }

    @GetMapping("/volume/users")
    @Operation(summary = "Top clients by USD trading volume", description = "Aggregates all accounts belonging to each current CLIENT. side=BUY ranks by amount bought and side=SELL by amount sold; omitted means gross (buy + sell). limit: 1–100; default 10.")
    public Report<List<UserVolume>> topUsers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) ReportingFilter.Side side,
            @RequestParam(defaultValue = "10") int limit) {
        return reports.topUsers(new ReportingFilter(from, to, assetType), side, limit);
    }

    @GetMapping("/volume/sides")
    @Operation(summary = "BUY and SELL USD volume", description = "Both sides are returned, including zero-volume sides. Failed execution attempts and unfilled orders are excluded.")
    public Report<List<SideVolume>> sides(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AssetType assetType) {
        return reports.sides(new ReportingFilter(from, to, assetType));
    }

    @GetMapping("/orders")
    @Operation(summary = "Submitted orders and their current statuses", description = "submittedTotal counts every persisted order submitted in the range, including orders now filled/rejected/cancelled. currentStatusCounts.SUBMITTED means orders still in that state; this is not an order transition history.")
    public Report<OrderMetrics> orders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AssetType assetType) {
        return reports.orders(new ReportingFilter(from, to, assetType));
    }

    @GetMapping("/users")
    @Operation(summary = "Active/blacklisted clients and clients who traded", description = "activeClients means current CLIENT status ACTIVE; blacklistedClients means current CLIENT status SUSPENDED. These population counts ignore range/asset filters. tradingClientsInPeriod counts distinct current clients with at least one trade matching those filters, not logins or accounts.")
    public Report<UserMetrics> users(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AssetType assetType) {
        return reports.users(new ReportingFilter(from, to, assetType));
    }

    @GetMapping("/etl")
    @Operation(summary = "Last successful reporting snapshot and refresh status", description = "initialized=false means no successful refresh yet. Attempt/running indicators are local to this application instance; committed snapshot metadata is shared.")
    public EtlStatus etlStatus() {
        return etl.status();
    }

    @PostMapping("/etl/refresh")
    @Operation(summary = "Refresh OLAP from a consistent OLTP snapshot", description = "Synchronous full refresh. Readers continue seeing the previous complete snapshot until commit. A competing refresh returns 409.")
    public RefreshResult refresh() {
        return etl.refresh();
    }
}
