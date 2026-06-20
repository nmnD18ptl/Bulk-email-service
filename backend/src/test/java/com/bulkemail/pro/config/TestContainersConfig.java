package com.bulkemail.pro.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration — one PostgreSQL and one Redis container
 * started once per JVM and reused across all integration test classes.
 *
 * Usage: annotate your integration test class with
 *   @SpringBootTest
 *   @ActiveProfiles("test")
 *   @Import(TestContainersConfig.class)
 */
@TestConfiguration
@Testcontainers
public class TestContainersConfig {

    @Container
    @SuppressWarnings("resource") // lifecycle managed by @Testcontainers
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("bulkemail_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    @Container
    @SuppressWarnings("resource") // lifecycle managed by @Testcontainers
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url",          POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",         POSTGRES::getUsername);
        registry.add("spring.flyway.password",     POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
    }
}
