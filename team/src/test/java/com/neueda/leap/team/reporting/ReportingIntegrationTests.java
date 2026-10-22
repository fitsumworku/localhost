package com.neueda.leap.team.reporting;

import com.neueda.leap.team.TeamApplication;
import com.neueda.leap.team.repository.UserRepository;
import com.neueda.leap.team.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Uses two fresh disposable PostgreSQL databases by default. External URLs are
 * ONLY for dedicated disposable test databases: their public tables are reset.
 * Never point these variables at a development database containing your work.
 */
@SpringBootTest(classes = TeamApplication.class)
@AutoConfigureMockMvc
class ReportingIntegrationTests {
    private static PostgreSQLContainer sourceContainer, targetContainer;
    private static String sourceUrl, targetUrl, username, password;
    private static boolean pglite;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate source;
    @Autowired @Qualifier("olapJdbcTemplate") JdbcTemplate target;
    @Autowired JsonMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    private String admin, client;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        initialize();
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        registry.add("spring.datasource.url", () -> sourceUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("app.reporting.enabled", () -> "true");
        registry.add("app.reporting.datasource.jdbc-url", () -> targetUrl);
        registry.add("app.reporting.datasource.username", () -> username);
        registry.add("app.reporting.datasource.password", () -> password);
        registry.add("app.reporting.datasource.maximum-pool-size", () -> pglite ? "1" : "3");
        registry.add("app.reporting.etl.enabled", () -> "false");
        registry.add("app.reporting.etl.batch-size", () -> "2"); // Exercise multiple flushes per table.
        registry.add("app.securities.seed.enabled", () -> "false");
        registry.add("app.orders.worker-enabled", () -> "false");
        registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(key));
    }

    private static synchronized void initialize() throws Exception {
        if (sourceUrl != null) return;
        String externalSource = System.getenv("REPORTING_TEST_OLTP_JDBC_URL");
        String externalTarget = System.getenv("REPORTING_TEST_OLAP_JDBC_URL");
        if (externalSource != null || externalTarget != null) {
            if (!"true".equals(System.getenv("REPORTING_TEST_ALLOW_RESET"))) {
                throw new IllegalArgumentException("External reporting tests reset tables; explicitly set REPORTING_TEST_ALLOW_RESET=true for disposable databases only.");
            }
            if (externalSource == null || externalTarget == null || externalSource.equals(externalTarget)
                    || externalSource.toLowerCase(Locale.ROOT).contains("currentschema=")
                    || externalTarget.toLowerCase(Locale.ROOT).contains("currentschema=")) {
                throw new IllegalArgumentException("Supply two distinct disposable reporting database URLs without currentSchema.");
            }
            sourceUrl = externalSource; targetUrl = externalTarget;
            username = System.getenv().getOrDefault("REPORTING_TEST_DB_USERNAME", "postgres");
            password = System.getenv().getOrDefault("REPORTING_TEST_DB_PASSWORD", "postgres");
        } else {
            sourceContainer = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("reporting_test_oltp");
            targetContainer = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("reporting_test_olap");
            sourceContainer.start(); targetContainer.start();
            sourceUrl = sourceContainer.getJdbcUrl(); targetUrl = targetContainer.getJdbcUrl();
            username = sourceContainer.getUsername(); password = sourceContainer.getPassword();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                sourceContainer.stop(); targetContainer.stop();
            }, "reporting-test-cleanup"));
        }
        try (Connection c = DriverManager.getConnection(sourceUrl, username, password);
                Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT version()")) { r.next(); pglite = r.getString(1).contains("PGlite"); }
            s.execute(resource("db/oltp_schema.sql"));
        }
        try (Connection c = DriverManager.getConnection(targetUrl, username, password);
                Statement s = c.createStatement()) { s.execute(resource("db/olap_schema.sql")); }
    }

    @BeforeEach
    void fixture() throws Exception {
        source.execute(resource("db/oltp_schema.sql"));
        target.execute(resource("db/olap_schema.sql"));
        source.execute(resource("reporting/fixture.sql"));
        admin = jwt.generateToken(users.findById(1L).orElseThrow()).token();
        client = jwt.generateToken(users.findById(2L).orElseThrow()).token();
    }

    @Test void refreshIsRepeatableAndNeverChangesOperationalData() throws Exception {
        var before = fingerprint();
        mvc.perform(get("/reports/summary").header("Authorization", bearer(admin)))
                .andExpect(status().isServiceUnavailable());
        assertThat(getReport("/reports/etl").path("initialized").asBoolean()).isFalse();
        refresh();
        assertCounts();
        assertThat(getReport("/reports/etl").path("refreshCount").asLong()).isEqualTo(1);
        refresh();
        assertCounts();
        assertThat(getReport("/reports/etl").path("refreshCount").asLong()).isEqualTo(2);
        assertThat(fingerprint()).isEqualTo(before);
        var summary = getReport("/reports/summary").path("data");
        assertThat(summary.path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("370.00");
        assertThat(summary.path("volume").path("buyVolumeUsd").decimalValue()).isEqualByComparingTo("270.00");
        assertThat(summary.path("volume").path("sellVolumeUsd").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(summary.path("volume").path("tradeCount").asLong()).isEqualTo(7);
        assertThat(summary.path("orders").path("submittedTotal").asLong()).isEqualTo(12);
        assertThat(summary.path("orders").path("currentStatusCounts").path("FILLED").asLong()).isEqualTo(7);
        for (String status : List.of("SUBMITTED", "ACCEPTED", "IN_EXECUTION", "REJECTED", "CANCELLED")) {
            assertThat(summary.path("orders").path("currentStatusCounts").path(status).asLong()).isEqualTo(1);
        }
        var population = summary.path("users");
        assertThat(population.path("totalClients").asLong()).isEqualTo(4);
        assertThat(population.path("activeClients").asLong()).isEqualTo(3);
        assertThat(population.path("blacklistedClients").asLong()).isEqualTo(1);
        assertThat(population.path("totalAdmins").asLong()).isEqualTo(2);
        assertThat(population.path("tradingClientsInPeriod").asLong()).isEqualTo(3);
    }

    @Test void reportsRespectExactPeriodBoundsAndSubmissionCohorts() throws Exception {
        refresh();
        String range = "?from=2026-01-10T00:00:00Z&to=2026-01-11T00:00:00Z";
        var summary = getReport("/reports/summary" + range).path("data");
        assertThat(summary.path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("160.00");
        assertThat(summary.path("volume").path("buyVolumeUsd").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(summary.path("volume").path("sellVolumeUsd").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(summary.path("volume").path("tradeCount").asLong()).isEqualTo(4);
        assertThat(summary.path("users").path("tradingClientsInPeriod").asLong()).isEqualTo(2);
        assertThat(summary.path("users").path("activeClients").asLong()).isEqualTo(3);
        assertThat(summary.path("orders").path("submittedTotal").asLong()).isEqualTo(7);
        assertThat(summary.path("orders").path("currentStatusCounts").path("FILLED").asLong()).isEqualTo(2);
        var equity = getReport("/reports/summary" + range + "&assetType=EQUITY").path("data");
        assertThat(equity.path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("130.00");
        var etf = getReport("/reports/summary?assetType=ETF").path("data");
        assertThat(etf.path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("30.00");
    }

    @Test void groupedReportsUseDistinctSecuritiesAndAggregateAllClientAccounts() throws Exception {
        refresh();
        var sectors = getReport("/reports/volume/sectors").path("data");
        assertThat(sectors.get(0).path("sector").asText()).isEqualTo("Technology");
        assertThat(sectors.get(0).path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("160.00");
        assertThat(sectors.get(1).path("sector").asText()).isEqualTo("Unclassified");
        assertThat(sectors.get(1).path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("140.00");
        var securities = getReport("/reports/volume/securities?assetType=EQUITY").path("data");
        assertThat(securities.size()).isEqualTo(3);
        assertThat(securities.get(0).path("ticker").asText()).isEqualTo("APL");
        assertThat(securities.get(0).path("exchange").asText()).isEqualTo("NASDAQ");
        assertThat(securities.get(2).path("ticker").asText()).isEqualTo("APL");
        assertThat(securities.get(2).path("exchange").asText()).isEqualTo("NYSE");
        var top = getReport("/reports/volume/users?limit=1").path("data");
        assertThat(top.size()).isEqualTo(1);
        assertThat(top.get(0).path("userId").asLong()).isEqualTo(2);
        assertThat(top.get(0).path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("170.00");
        var sides = getReport("/reports/volume/sides").path("data");
        assertThat(sides.size()).isEqualTo(2);
        assertThat(sides.get(0).path("volumeUsd").decimalValue().add(sides.get(1).path("volumeUsd").decimalValue())).isEqualByComparingTo("370.00");
    }

    @Test void refreshUpdatesBlacklistAndOrderStatusesWithoutDuplicatingHistory() throws Exception {
        refresh();
        source.update("UPDATE users SET status='SUSPENDED',updated_date=clock_timestamp() WHERE user_id=2");
        source.update("UPDATE orders SET order_status='CANCELLED',terminal_at=clock_timestamp(),updated_date=clock_timestamp() WHERE order_id=9");
        assertThat(getReport("/reports/users").path("data").path("blacklistedClients").asLong()).isEqualTo(1);
        refresh();
        var users = getReport("/reports/users").path("data");
        assertThat(users.path("activeClients").asLong()).isEqualTo(2);
        assertThat(users.path("blacklistedClients").asLong()).isEqualTo(2);
        var orders = getReport("/reports/orders").path("data");
        assertThat(orders.path("currentStatusCounts").path("ACCEPTED").asLong()).isZero();
        assertThat(orders.path("currentStatusCounts").path("CANCELLED").asLong()).isEqualTo(2);
        assertCounts();
        assertThat(getReport("/reports/summary").path("data").path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("370.00");
    }

    @Test void failedRefreshRollsBackToPreviousCompleteSnapshot() throws Exception {
        refresh();
        var before = getReport("/reports/summary");
        target.execute("ALTER TABLE fact_trades ADD CONSTRAINT test_reject_reload CHECK (trade_value < 100) NOT VALID");
        source.update("UPDATE users SET status='SUSPENDED',updated_date=clock_timestamp() WHERE user_id=2");
        try {
            mvc.perform(post("/reports/etl/refresh").header("Authorization", bearer(admin)))
                    .andExpect(status().isServiceUnavailable());
            assertThat(getReport("/reports/summary")).isEqualTo(before);
            assertThat(getReport("/reports/etl").path("refreshCount").asLong()).isEqualTo(1);
            assertThat(getReport("/reports/etl").path("lastAttemptOutcome").asText()).isEqualTo("FAILED");
            assertCounts();
        } finally { target.execute("ALTER TABLE fact_trades DROP CONSTRAINT test_reject_reload"); }
        refresh();
        assertThat(getReport("/reports/users").path("data").path("blacklistedClients").asLong()).isEqualTo(2);
    }

    @Test void adminOnlyAccessAndInvalidFiltersAreEnforced() throws Exception {
        mvc.perform(get("/reports/summary")).andExpect(status().isUnauthorized());
        for (String route : List.of("summary", "volume/sectors", "volume/securities", "volume/users", "volume/sides", "orders", "users", "etl")) {
            mvc.perform(get("/reports/" + route).header("Authorization", bearer(client))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/reports/etl/refresh").header("Authorization", bearer(client))).andExpect(status().isForbidden());
        refresh();
        for (String route : List.of("summary?from=2026-01-11T00:00:00Z&to=2026-01-10T00:00:00Z",
                "summary?from=not-a-date", "summary?assetType=BOND", "volume/users?limit=0", "volume/securities?limit=101")) {
            mvc.perform(get("/reports/" + route).header("Authorization", bearer(admin))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/accounts").header("Authorization", bearer(client))).andExpect(status().isOk());
        mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@etl.test\",\"password\":\"DemoPass123!\"}"))
                .andExpect(status().isOk());
    }

    @Test void successfulEmptySnapshotReturnsZeroInsteadOfNoSnapshotError() throws Exception {
        source.execute("TRUNCATE accounts,securities CASCADE");
        source.update("DELETE FROM users WHERE role_id=(SELECT role_id FROM roles WHERE name='CLIENT')");
        refresh();
        var summary = getReport("/reports/summary").path("data");
        assertThat(summary.path("volume").path("grossVolumeUsd").decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.path("orders").path("submittedTotal").asLong()).isZero();
        assertThat(summary.path("users").path("totalClients").asLong()).isZero();
        assertThat(getReport("/reports/volume/sides").path("data").size()).isEqualTo(2);
    }

    @Test void competingNativePostgresRefreshReturnsConflict() throws Exception {
        assumeFalse(pglite, "PGlite cannot validate independent PostgreSQL backend locks");
        try (Connection c = DriverManager.getConnection(targetUrl, username, password);
                PreparedStatement s = c.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            c.setAutoCommit(false);
            s.setLong(1, 0x504159535052494EL); s.execute();
            mvc.perform(post("/reports/etl/refresh").header("Authorization", bearer(admin))).andExpect(status().isConflict());
            c.rollback();
        }
        refresh();
    }

    private void refresh() throws Exception {
        mvc.perform(post("/reports/etl/refresh").header("Authorization", bearer(admin))).andExpect(status().isOk());
    }
    private JsonNode getReport(String path) throws Exception {
        var response = mvc.perform(get(path).header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }
    private static String bearer(String token) { return "Bearer " + token; }
    private void assertCounts() {
        for (var entry : Map.of("dim_user",6L,"dim_account",5L,"dim_security",6L,"fact_orders",12L,
                "fact_executions",8L,"fact_trades",7L,"fact_transactions",3L,"fact_disputes",2L).entrySet()) {
            assertThat(target.queryForObject("SELECT count(*) FROM " + entry.getKey(), Long.class)).as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }
    private Map<String,String> fingerprint() {
        var result = new LinkedHashMap<String,String>();
        for (String table : List.of("roles","users","accounts","account_cash_balances","securities","account_positions",
                "orders","order_reservations","executions","trades","transactions","disputes","cash_ledger","audit_logs")) {
            result.put(table, source.queryForObject("SELECT md5(coalesce(string_agg(value,',' ORDER BY value),'')) FROM (SELECT row_to_json(t)::text value FROM " + table + " t) rows", String.class));
        }
        return result;
    }
    private static String resource(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
