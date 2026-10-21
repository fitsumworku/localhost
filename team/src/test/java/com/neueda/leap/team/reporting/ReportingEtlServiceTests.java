package com.neueda.leap.team.reporting;

import com.neueda.leap.team.exception.ApiException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportingEtlServiceTests {
    private DataSource source;
    private DataSource target;
    private Connection destination;
    private ReportingEtlService service;

    @BeforeEach
    void setUp() throws SQLException {
        source = mock(DataSource.class);
        target = mock(DataSource.class);
        destination = mock(Connection.class);
        when(target.getConnection()).thenReturn(destination);
        service = new ReportingEtlService(source, target, mock(JdbcTemplate.class),
                JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC), 500, 120);
    }

    @Test
    void anotherInstanceHoldingRefreshLockDoesNotEvenAcquireSourceConnection() throws SQLException {
        PreparedStatement lock = statementResult(false);
        when(destination.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")).thenReturn(lock);

        ApiException failure = assertThrows(ApiException.class, service::refresh);

        assertEquals(HttpStatus.CONFLICT, failure.status());
        assertEquals("REPORTING_REFRESH_IN_PROGRESS", failure.code());
        verifyNoInteractions(source);
        verify(destination).rollback();
        verify(destination, never()).commit();
        verify(destination).close();
    }

    @Test
    void destinationContainingOperationalTablesIsRejectedBeforeAnyDeleteOrSourceRead() throws SQLException {
        PreparedStatement lock = statementResult(true);
        PreparedStatement operationalTables = statementResult(true);
        when(destination.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")).thenReturn(lock);
        when(destination.prepareStatement(contains("to_regclass"))).thenReturn(operationalTables);

        ApiException failure = assertThrows(ApiException.class, service::refresh);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
        verifyNoInteractions(source);
        verify(destination, never()).prepareStatement(startsWith("DELETE"));
        verify(destination).rollback();
        verify(destination, never()).commit();
    }

    @Test
    void incompatibleSchemaIsRejectedWithoutOpeningSource() throws SQLException {
        allowLockAndSeparateDatabase();
        PreparedStatement metadata = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(metadata.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(2);
        when(destination.prepareStatement(startsWith("SELECT schema_version"))).thenReturn(metadata);

        ApiException failure = assertThrows(ApiException.class, service::refresh);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
        verifyNoInteractions(source);
        verify(destination, never()).prepareStatement(startsWith("DELETE"));
        verify(destination).rollback();
    }

    @Test
    void sourceReadFailureRollsBackBothTransactionsAndEnforcesReadOnlySnapshot() throws SQLException {
        allowLockAndSeparateDatabase();
        PreparedStatement metadata = mock(PreparedStatement.class);
        ResultSet version = mock(ResultSet.class);
        when(metadata.executeQuery()).thenReturn(version);
        when(version.next()).thenReturn(true);
        when(version.getInt(1)).thenReturn(1);
        when(destination.prepareStatement(startsWith("SELECT schema_version"))).thenReturn(metadata);
        Connection origin = mock(Connection.class);
        PreparedStatement readOnly = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(origin);
        when(origin.prepareStatement("SET TRANSACTION READ ONLY")).thenReturn(readOnly);
        when(origin.prepareStatement("SELECT clock_timestamp()")).thenThrow(new SQLException("Source disconnected", "08006"));

        ApiException failure = assertThrows(ApiException.class, service::refresh);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
        var ordering = inOrder(destination, source, origin);
        ordering.verify(destination).prepareStatement("SELECT pg_try_advisory_xact_lock(?)");
        ordering.verify(source).getConnection();
        ordering.verify(origin).setReadOnly(true);
        ordering.verify(origin).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        ordering.verify(origin).setAutoCommit(false);
        ordering.verify(origin).prepareStatement("SET TRANSACTION READ ONLY");
        verify(origin).rollback();
        verify(origin).close();
        verify(destination).rollback();
        verify(destination, never()).commit();
        assertFalse(failure.getMessage().contains("Source disconnected"));
    }

    private void allowLockAndSeparateDatabase() throws SQLException {
        PreparedStatement lock = statementResult(true);
        PreparedStatement operationalTables = statementResult(false);
        when(destination.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")).thenReturn(lock);
        when(destination.prepareStatement(contains("to_regclass"))).thenReturn(operationalTables);
    }

    private PreparedStatement statementResult(boolean value) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(value);
        return statement;
    }
}
