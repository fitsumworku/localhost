package com.neueda.leap.team.support;

import com.neueda.leap.team.TeamApplication;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A private schema per test JVM; the application's development database is never reset. */
@SpringBootTest(classes = TeamApplication.class)
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {
    private static PostgreSQLContainer postgres;
    private static String url;
    private static String username;
    private static String password;
    private static String schema;
    private static boolean pglite;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        initialize();
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        registry.add("app.orders.worker-enabled", () -> "false");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> pglite ? "1" : "8");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("jwt.secret", () -> Base64.getEncoder().encodeToString(key));
        registry.add("jwt.issuer", () -> "account-test-api");
        registry.add("jwt.audience", () -> "account-test-client");
        registry.add("jwt.expiration-ms", () -> "900000");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:4200,http://localhost:8090");
    }

    private static synchronized void initialize() throws Exception {
        if (url != null) return;
        String external = System.getenv("ACCOUNT_TEST_JDBC_URL");
        String base;
        if (external == null || external.isBlank()) {
            postgres = new PostgreSQLContainer("postgres:16-alpine");
            postgres.start();
            base = postgres.getJdbcUrl(); username = postgres.getUsername(); password = postgres.getPassword();
        } else {
            // Never inherit a developer's configured search path for a DROP/recreate schema script.
            if (external.toLowerCase(Locale.ROOT).contains("currentschema=")) {
                throw new IllegalArgumentException("ACCOUNT_TEST_JDBC_URL must omit currentSchema; tests create their own schema.");
            }
            base = external;
            username = System.getenv().getOrDefault("ACCOUNT_TEST_DB_USERNAME", "postgres");
            password = System.getenv().getOrDefault("ACCOUNT_TEST_DB_PASSWORD", "postgres");
        }
        schema = "account_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(base, username, password); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
            try (ResultSet r = s.executeQuery("SELECT version()")) { r.next(); pglite = r.getString(1).contains("PGlite"); }
        }
        url = base + (base.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        try (Connection c = DriverManager.getConnection(url, username, password); Statement s = c.createStatement();
                var input = new ClassPathResource("db/oltp_schema.sql").getInputStream()) {
            s.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Connection c = DriverManager.getConnection(url, username, password); Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA " + schema + " CASCADE");
            } catch (SQLException ignored) { /* A stopped disposable container already removed the test data. */ }
            if (postgres != null) postgres.stop();
        }, "account-test-cleanup"));
    }
    protected static boolean nativePostgres() { return !pglite; }
}
