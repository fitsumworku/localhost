package com.neueda.leap.team.reporting;

import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.reporting.ReportingDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.reporting", name = "enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@Transactional(transactionManager = "olapTransactionManager", readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportingService {
    private static final String VOLUME_COLUMNS = """
            COALESCE(SUM(t.trade_value), 0) AS gross_volume,
            COALESCE(SUM(t.trade_value) FILTER (WHERE t.trade_type = 'B'), 0) AS buy_volume,
            COALESCE(SUM(t.trade_value) FILTER (WHERE t.trade_type = 'S'), 0) AS sell_volume,
            COUNT(*) AS trade_count,
            COUNT(*) FILTER (WHERE t.trade_type = 'B') AS buy_count,
            COUNT(*) FILTER (WHERE t.trade_type = 'S') AS sell_count
            """;
    private static final String TRADE_FROM = """
            FROM fact_trades t
            JOIN dim_security s ON s.security_key = t.security_key
            """;
    private static final String SECTOR = "COALESCE(NULLIF(BTRIM(s.sector), ''), 'Unclassified')";
    private final JdbcTemplate jdbc;

    public ReportingService(@Qualifier("olapJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Report<Summary> summary(ReportingFilter filter) {
        Freshness freshness = requireFreshness();
        return new Report<>(filter, freshness,
                new Summary(volume(filter), orderMetrics(filter), userMetrics(filter)));
    }

    public Report<List<SectorVolume>> sectors(ReportingFilter filter, int limit) {
        ReportingFilter.validateLimit(limit);
        Freshness freshness = requireFreshness();
        SqlFilter where = where(filter, "t.traded_at");
        var rows = jdbc.query("SELECT " + SECTOR + " AS sector_name, " + VOLUME_COLUMNS
                        + TRADE_FROM + where.clause()
                        + " GROUP BY " + SECTOR + " ORDER BY gross_volume DESC, sector_name ASC LIMIT ?",
                (rs, rowNum) -> new SectorVolume(rs.getString("sector_name"), readVolume(rs)),
                where.argsWithLimit(limit));
        return new Report<>(filter, freshness, rows);
    }

    public Report<List<SecurityVolume>> securities(ReportingFilter filter, int limit) {
        ReportingFilter.validateLimit(limit);
        Freshness freshness = requireFreshness();
        SqlFilter where = where(filter, "t.traded_at");
        var rows = jdbc.query("""
                        SELECT s.security_id, s.ticker, s.company_name, s.exchange, s.asset_type,
                        """ + SECTOR + " AS sector_name, " + VOLUME_COLUMNS + """
                        , SUM(t.shares) AS quantity_traded,
                        COALESCE(SUM(t.shares) FILTER (WHERE t.trade_type = 'B'), 0) AS buy_quantity,
                        COALESCE(SUM(t.shares) FILTER (WHERE t.trade_type = 'S'), 0) AS sell_quantity
                        """ + TRADE_FROM + where.clause() + """
                        GROUP BY s.security_key, s.security_id, s.ticker, s.company_name,
                                 s.exchange, s.asset_type, s.sector
                        ORDER BY gross_volume DESC, s.security_id ASC LIMIT ?
                        """,
                (rs, rowNum) -> new SecurityVolume(rs.getLong("security_id"), rs.getString("ticker"),
                        rs.getString("company_name"), rs.getString("exchange"), rs.getString("asset_type"),
                        rs.getString("sector_name"), readVolume(rs), rs.getBigDecimal("quantity_traded"),
                        rs.getBigDecimal("buy_quantity"), rs.getBigDecimal("sell_quantity")),
                where.argsWithLimit(limit));
        return new Report<>(filter, freshness, rows);
    }

    public Report<List<UserVolume>> topUsers(ReportingFilter filter, int limit) {
        ReportingFilter.validateLimit(limit);
        Freshness freshness = requireFreshness();
        SqlFilter where = where(filter, "t.traded_at");
        var rows = jdbc.query("SELECT u.user_id, u.user_name, " + VOLUME_COLUMNS + TRADE_FROM
                        + " JOIN dim_user u ON u.user_key = t.user_key " + where.clause()
                        + " AND u.role_name = 'CLIENT'"
                        + " GROUP BY u.user_key, u.user_id, u.user_name"
                        + " ORDER BY gross_volume DESC, u.user_id ASC LIMIT ?",
                (rs, rowNum) -> new UserVolume(rs.getLong("user_id"), rs.getString("user_name"), readVolume(rs)),
                where.argsWithLimit(limit));
        return new Report<>(filter, freshness, rows);
    }

    public Report<List<SideVolume>> sides(ReportingFilter filter) {
        Freshness freshness = requireFreshness();
        VolumeTotals v = volume(filter);
        return new Report<>(filter, freshness, List.of(
                new SideVolume("BUY", v.buyVolumeUsd(), v.buyTradeCount()),
                new SideVolume("SELL", v.sellVolumeUsd(), v.sellTradeCount())));
    }

    public Report<OrderMetrics> orders(ReportingFilter filter) {
        Freshness freshness = requireFreshness();
        return new Report<>(filter, freshness, orderMetrics(filter));
    }

    public Report<UserMetrics> users(ReportingFilter filter) {
        Freshness freshness = requireFreshness();
        return new Report<>(filter, freshness, userMetrics(filter));
    }

    private Freshness requireFreshness() {
        var rows = jdbc.query("""
                SELECT schema_version, source_snapshot_at, last_successful_at
                FROM etl_metadata WHERE metadata_id = 1
                """, (rs, rowNum) -> {
            if (rs.getInt("schema_version") != 1) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REPORTING_UNAVAILABLE",
                        "The reporting database is unavailable or needs its schema updated.");
            }
            Timestamp source = rs.getTimestamp("source_snapshot_at");
            Timestamp loaded = rs.getTimestamp("last_successful_at");
            return new Freshness(source == null ? null : source.toInstant(), loaded == null ? null : loaded.toInstant());
        });
        if (rows.size() != 1 || rows.get(0).sourceSnapshotAt() == null || rows.get(0).lastSuccessfulAt() == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REPORTING_NOT_INITIALIZED",
                    "Reporting data has not been loaded yet. Refresh ETL before requesting metrics.");
        }
        return rows.get(0);
    }

    private VolumeTotals volume(ReportingFilter filter) {
        SqlFilter where = where(filter, "t.traded_at");
        return jdbc.queryForObject("SELECT " + VOLUME_COLUMNS + TRADE_FROM + where.clause(),
                (rs, rowNum) -> readVolume(rs), where.args());
    }

    private OrderMetrics orderMetrics(ReportingFilter filter) {
        SqlFilter where = where(filter, "o.submitted_at");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("SUBMITTED", "ACCEPTED", "IN_EXECUTION", "FILLED", "REJECTED", "CANCELLED")) {
            counts.put(status, 0L);
        }
        jdbc.query("""
                SELECT os.order_status_code, COUNT(*) AS order_count
                FROM fact_orders o
                JOIN dim_order_status os ON os.order_status_key = o.order_status_key
                JOIN dim_security s ON s.security_key = o.security_key
                """ + where.clause() + " GROUP BY os.order_status_code",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        counts.put(rs.getString("order_status_code"), rs.getLong("order_count")), where.args());
        long submittedTotal = counts.values().stream().mapToLong(Long::longValue).sum();
        return new OrderMetrics(submittedTotal, Collections.unmodifiableMap(counts));
    }

    private UserMetrics userMetrics(ReportingFilter filter) {
        long[] population = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE role_name = 'CLIENT') AS total_clients,
                       COUNT(*) FILTER (WHERE role_name = 'CLIENT' AND status = 'ACTIVE') AS active_clients,
                       COUNT(*) FILTER (WHERE role_name = 'CLIENT' AND status = 'SUSPENDED') AS blacklisted_clients,
                       COUNT(*) FILTER (WHERE role_name = 'ADMIN') AS total_admins
                FROM dim_user
                """, (rs, rowNum) -> new long[] {rs.getLong("total_clients"), rs.getLong("active_clients"),
                rs.getLong("blacklisted_clients"), rs.getLong("total_admins")});
        SqlFilter where = where(filter, "t.traded_at");
        Long trading = jdbc.queryForObject("SELECT COUNT(DISTINCT u.user_key) " + TRADE_FROM
                        + " JOIN dim_user u ON u.user_key = t.user_key " + where.clause()
                        + " AND u.role_name = 'CLIENT'", Long.class, where.args());
        return new UserMetrics(population[0], population[1], population[2], population[3], trading);
    }

    private static VolumeTotals readVolume(ResultSet rs) throws SQLException {
        return new VolumeTotals(rs.getBigDecimal("gross_volume"), rs.getBigDecimal("buy_volume"),
                rs.getBigDecimal("sell_volume"), rs.getLong("trade_count"), rs.getLong("buy_count"), rs.getLong("sell_count"));
    }

    // SQL fragments are fixed server-side; every caller-supplied value remains a bound parameter.
    private static SqlFilter where(ReportingFilter filter, String timeColumn) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (filter.fromInclusive() != null) {
            sql.append(" AND ").append(timeColumn).append(" >= ? ");
            args.add(Timestamp.from(filter.fromInclusive()));
        }
        if (filter.toExclusive() != null) {
            sql.append(" AND ").append(timeColumn).append(" < ? ");
            args.add(Timestamp.from(filter.toExclusive()));
        }
        if (filter.assetType() != null) {
            sql.append(" AND s.asset_type = ? ");
            args.add(filter.assetType().name());
        }
        return new SqlFilter(sql.toString(), args.toArray());
    }

    private record SqlFilter(String clause, Object[] args) {
        Object[] argsWithLimit(int limit) {
            List<Object> result = new ArrayList<>(List.of(args));
            result.add(limit);
            return result.toArray();
        }
    }
}
