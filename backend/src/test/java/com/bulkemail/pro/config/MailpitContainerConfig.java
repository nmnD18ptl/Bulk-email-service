package com.bulkemail.pro.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration for Mailpit — a local SMTP catch-all server.
 *
 * Provides:
 *   - SMTP on a randomly mapped port (container port 1025)
 *   - REST API on a randomly mapped port (container port 8025)
 *
 * When imported into a @SpringBootTest, automatically configures
 * spring.mail.host/port so Spring Boot's JavaMailSender auto-configuration
 * points at the container.
 *
 * Usage:
 *   @SpringBootTest
 *   @ActiveProfiles("test")
 *   @Import({TestContainersConfig.class, MailpitContainerConfig.class})
 *   class MyTest { ... }
 *
 * Query captured emails: MailpitContainerConfig.getApiBaseUrl() + "/api/v1/messages"
 */
@TestConfiguration
@Testcontainers
public class MailpitContainerConfig {

    public static final int SMTP_PORT = 1025;
    public static final int API_PORT  = 8025;

    @Container
    @SuppressWarnings("resource") // lifecycle managed by @Testcontainers
    public static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
                    .withExposedPorts(SMTP_PORT, API_PORT)
                    .withReuse(true);

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(SMTP_PORT).toString());
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    }

    /** Base URL for the Mailpit HTTP API (e.g. {@code http://localhost:49152}). */
    public static String getApiBaseUrl() {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(API_PORT);
    }
}
