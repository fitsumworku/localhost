package com.neueda.leap.team.reporting;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Reporting uses JDBC only. Existing JPA repositories and JDBC services stay on OLTP. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.reporting.enabled", havingValue = "true")
public class ReportingConfig {
    // defaultCandidate=false preserves Boot's normal OLTP DataSource/JdbcTemplate
    // auto-configuration. Only explicitly qualified reporting code uses this pool.
    @Bean(name = "olapDataSource", defaultCandidate = false, destroyMethod = "close")
    @ConfigurationProperties("app.reporting.datasource")
    public HikariDataSource olapDataSource() {
        var pool = new HikariDataSource();
        pool.setPoolName("reporting-olap");
        pool.setMaximumPoolSize(4);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(5000);
        pool.setValidationTimeout(3000);
        // A temporary reporting outage must not prevent normal OLTP operations.
        pool.setInitializationFailTimeout(-1);
        return pool;
    }

    @Bean(name = "olapJdbcTemplate", defaultCandidate = false)
    public JdbcTemplate olapJdbcTemplate(@Qualifier("olapDataSource") DataSource dataSource,
            @Value("${app.reporting.query-timeout-seconds:120}") int queryTimeoutSeconds) {
        if (queryTimeoutSeconds < 1) {
            throw new IllegalArgumentException("Reporting query timeout must be positive.");
        }
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(queryTimeoutSeconds);
        return jdbc;
    }

    @Bean(name = "olapTransactionManager", defaultCandidate = false)
    public DataSourceTransactionManager olapTransactionManager(
            @Qualifier("olapDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
