package com.bulkemail.pro.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;

/**
 * Registers HikariCP pool metrics with Micrometer and logs the active
 * database URL at startup so operators can confirm the right database
 * is connected without exposing credentials.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public DatabaseConfig(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.setMetricRegistry(meterRegistry);
            log.info("Database connected: {} | pool={} max={} min={}",
                maskCredentials(datasourceUrl),
                hikari.getPoolName(),
                hikari.getMaximumPoolSize(),
                hikari.getMinimumIdle());
        }
    }

    /**
     * Strips username/password from JDBC URL before logging.
     * jdbc:postgresql://host:5432/db?user=foo&password=bar → jdbc:postgresql://host:5432/db
     */
    private String maskCredentials(String url) {
        if (url == null) return "unknown";
        return url.replaceAll("(?i)(user|password|pwd)=[^&;]*", "$1=***");
    }
}
