package com.neueda.leap.team.reporting;

import com.neueda.leap.team.exception.ApiException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Project-size full snapshot ETL. Deliberately not @Transactional: two explicitly
 * owned JDBC transactions prevent OLAP writes from joining the OLTP/JPA manager.
 */
@Service
@ConditionalOnProperty(name = "app.reporting.enabled", havingValue = "true")
public class ReportingEtlService {
    public static final int SCHEMA_VERSION = 1;
    // Same lock ID on every app instance. Transaction-scoped: release on commit,
    // rollback, crash or connection loss without an explicit unlock operation.
    private static final long REFRESH_LOCK_ID = 0x504159535052494EL;
    private static final Logger log = LoggerFactory.getLogger(ReportingEtlService.class);
    private static final TypeReference<Map<String, Long>> ROW_COUNTS = new TypeReference<>() {};
    private final DataSource sourceDataSource;
    private final DataSource targetDataSource;
    private final JdbcTemplate olap;
    private final JsonMapper json;
    private final Clock clock;
    private final int batchSize;
    private final int timeoutSeconds;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Attempt> attempt = new AtomicReference<>(new Attempt(null, "IDLE"));

    public ReportingEtlService(@Qualifier("dataSource") DataSource sourceDataSource,
                               @Qualifier("olapDataSource") DataSource targetDataSource,
                               @Qualifier("olapJdbcTemplate") JdbcTemplate olap,
                               JsonMapper json, Clock clock,
                               @Value("${app.reporting.etl.batch-size:500}") int batchSize,
                               @Value("${app.reporting.query-timeout-seconds:120}") int timeoutSeconds) {
        if (batchSize < 1 || batchSize > 10000) throw new IllegalArgumentException("Reporting batch size must be between 1 and 10000");
        if (timeoutSeconds < 1 || timeoutSeconds > 3600) throw new IllegalArgumentException("Reporting query timeout must be between 1 and 3600 seconds");
        this.sourceDataSource = sourceDataSource;
        this.targetDataSource = targetDataSource;
        this.olap = olap;
        this.json = json;
        this.clock = clock;
        this.batchSize = batchSize;
        this.timeoutSeconds = timeoutSeconds;
    }

    public RefreshResult refresh() {
        if (!running.compareAndSet(false, true)) throw busy();
        Instant attemptedAt = clock.instant();
        attempt.set(new Attempt(attemptedAt, "RUNNING"));
        long startedNanos = System.nanoTime();
        try (Connection target = targetDataSource.getConnection()) {
            target.setReadOnly(false);
            target.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            target.setAutoCommit(false);
            try {
                // Lock BEFORE source snapshot creation. Otherwise an older reader
                // could wait for a newer refresh and overwrite it after that commit.
                if (!tryRefreshLock(target)) throw busy();
                verifyTarget(target);
                RefreshResult result;
                try (Connection source = sourceDataSource.getConnection()) {
                    source.setReadOnly(true);
                    source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    source.setAutoCommit(false);
                    try {
                        // Enforce read-only on the server too, even if the JDBC
                        // driver's readOnlyMode is configured to ignore the hint.
                        execute(source, "SET TRANSACTION READ ONLY");
                        Instant snapshotAt = snapshotTimestamp(source);
                        Map<String, Long> counts = new ReportingSnapshotLoader(source, target, batchSize, timeoutSeconds).replace();
                        source.commit(); // End the coherent, read-only source snapshot.
                        long durationMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
                        Instant completedAt = clock.instant();
                        try (PreparedStatement statement = target.prepareStatement("""
                                UPDATE public.etl_metadata SET source_snapshot_at = ?, last_successful_at = ?,
                                    duration_ms = ?, refresh_count = refresh_count + 1, row_counts = ?::jsonb
                                WHERE metadata_id = 1 AND schema_version = ?
                                """)) {
                            statement.setQueryTimeout(timeoutSeconds);
                            statement.setTimestamp(1, Timestamp.from(snapshotAt));
                            statement.setTimestamp(2, Timestamp.from(completedAt));
                            statement.setLong(3, durationMs);
                            statement.setString(4, json.writeValueAsString(counts));
                            statement.setInt(5, SCHEMA_VERSION);
                            if (statement.executeUpdate() != 1) throw new SQLException("Missing ETL metadata row");
                        }
                        result = new RefreshResult(snapshotAt, completedAt, durationMs, counts);
                    } catch (SQLException | RuntimeException failure) {
                        rollback(source, failure);
                        throw failure;
                    }
                }
                target.commit(); // Data and its freshness metadata become visible together.
                attempt.set(new Attempt(attemptedAt, "SUCCEEDED"));
                log.info("OLAP snapshot committed: sourceSnapshotAt={}, rowCounts={}", result.sourceSnapshotAt(), result.rowCounts());
                return result;
            } catch (SQLException | RuntimeException failure) {
                rollback(target, failure);
                throw failure;
            }
        } catch (ApiException exception) {
            attempt.set(new Attempt(attemptedAt, "REPORTING_REFRESH_IN_PROGRESS".equals(exception.code()) ? "SKIPPED_LOCKED" : "FAILED"));
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            attempt.set(new Attempt(attemptedAt, "FAILED"));
            // JDBC batch exceptions can embed rejected row values. Keep those out
            // of logs as well as HTTP responses (dispute text may be personal).
            log.error("OLAP refresh could not complete: exceptionType={}, sqlState={}",
                    exception.getClass().getSimpleName(), sqlState(exception));
            throw unavailable();
        } finally {
            running.set(false);
        }
    }

    public EtlStatus status() {
        Attempt localAttempt = attempt.get();
        try {
            EtlStatus status = olap.queryForObject("""
                    SELECT schema_version, source_snapshot_at, last_successful_at,
                           duration_ms, refresh_count, row_counts::text
                    FROM public.etl_metadata WHERE metadata_id = 1
                    """, (result, row) -> {
                int version = result.getInt("schema_version");
                if (version != SCHEMA_VERSION) throw new SQLException("Unsupported OLAP schema version");
                Timestamp lastSuccess = result.getTimestamp("last_successful_at");
                Timestamp sourceAt = result.getTimestamp("source_snapshot_at");
                return new EtlStatus(version, lastSuccess != null,
                        sourceAt == null ? null : sourceAt.toInstant(),
                        lastSuccess == null ? null : lastSuccess.toInstant(),
                        result.getObject("duration_ms", Long.class), result.getLong("refresh_count"),
                        json.readValue(result.getString("row_counts"), ROW_COUNTS), running.get(),
                        localAttempt.at(), localAttempt.outcome());
            });
            if (status == null) throw unavailable();
            return status;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private boolean tryRefreshLock(Connection target) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
            statement.setQueryTimeout(timeoutSeconds);
            statement.setLong(1, REFRESH_LOCK_ID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("No advisory lock result");
                return result.getBoolean(1);
            }
        }
    }

    private void verifyTarget(Connection target) throws SQLException {
        // Detect a misdirected URL even when two hostnames alias the same server.
        // The reporting database must be separate and must not contain OLTP tables.
        try (PreparedStatement statement = target.prepareStatement("""
                SELECT to_regclass('public.users') IS NOT NULL
                    OR to_regclass('public.orders') IS NOT NULL AS contains_oltp
                """)) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getBoolean(1)) {
                    throw new SQLException("OLAP must point to a separate database without OLTP tables");
                }
            }
        }
        try (PreparedStatement statement = target.prepareStatement("SELECT schema_version FROM public.etl_metadata WHERE metadata_id = 1")) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != SCHEMA_VERSION) {
                    throw new SQLException("Initialize the compatible OLAP schema before running ETL");
                }
            }
        }
    }

    private Instant snapshotTimestamp(Connection source) throws SQLException {
        try (PreparedStatement statement = source.prepareStatement("SELECT clock_timestamp()")) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("No source snapshot timestamp");
                return result.getTimestamp(1).toInstant();
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeoutSeconds);
            statement.execute();
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try { connection.rollback(); }
        catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }

    private static String sqlState(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) return sql.getSQLState();
        }
        return null;
    }

    private static ApiException busy() {
        return new ApiException(HttpStatus.CONFLICT, "REPORTING_REFRESH_IN_PROGRESS", "A reporting refresh is already running. Try again shortly.");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REPORTING_UNAVAILABLE", "Reporting is unavailable. Check the OLAP connection and initialize its compatible schema.");
    }

    private record Attempt(Instant at, String outcome) {}
}
