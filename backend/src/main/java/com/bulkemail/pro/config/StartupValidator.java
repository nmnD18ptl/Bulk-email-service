package com.bulkemail.pro.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that all required environment variables are set before the
 * application begins serving traffic.  Active only in the "prod" profile.
 *
 * Why check env vars explicitly rather than relying on Spring's placeholder
 * resolution?  Spring fails one variable at a time with a cryptic message.
 * This validator reports every missing variable in a single log statement,
 * making Railway deployment failures immediately actionable.
 *
 * If any required variable is absent or blank, the application fails fast
 * with a clear error rather than starting in a broken state.
 */
@Component
@Profile("prod")
@Slf4j
public class StartupValidator {

    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * Variables that must be present and non-blank for the app to function.
     * Spring's YAML binding already catches DB_URL / DB_USERNAME / DB_PASSWORD
     * at context creation time; the list below catches variables that might
     * be resolved later (e.g. at first request) and provides one combined
     * error message for operators.
     */
    private static final String[] REQUIRED = {
        "DB_URL",
        "DB_USERNAME",
        "DB_PASSWORD",
        "REDIS_URL",
        "RABBITMQ_URL",
        "JWT_SECRET",
        "ENCRYPTION_KEY",
        "CORS_ORIGINS",
        "TRACKING_BASE_URL",
        "UNSUBSCRIBE_BASE_URL",
    };

    @PostConstruct
    void validate() {
        List<String> missing  = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String var : REQUIRED) {
            String value = System.getenv(var);
            if (value == null || value.isBlank()) {
                missing.add(var);
            }
        }

        // Security-strength checks for secret values.
        checkSecretLength("JWT_SECRET",     missing, warnings);
        checkSecretLength("ENCRYPTION_KEY", missing, warnings);

        // CORS origins must use HTTPS in production.
        String cors = System.getenv("CORS_ORIGINS");
        if (cors != null && !cors.isBlank() && cors.contains("http://")) {
            warnings.add("CORS_ORIGINS contains http:// — use https:// in production");
        }

        if (!warnings.isEmpty()) {
            log.warn("=== Startup warnings ===");
            warnings.forEach(w -> log.warn("  ⚠  {}", w));
        }

        if (!missing.isEmpty()) {
            log.error("=================================================================");
            log.error("STARTUP FAILED — missing or invalid environment variables:");
            missing.forEach(v -> log.error("  ✗  {}", v));
            log.error("Set these variables in Railway → Variables and redeploy.");
            log.error("See .env.example in the repository for the required format.");
            log.error("=================================================================");
            throw new IllegalStateException(
                "Missing required environment variables: " + missing);
        }

        log.info("Startup validation passed — all {} required environment variables are set",
            REQUIRED.length);
    }

    private void checkSecretLength(String varName, List<String> missing, List<String> warnings) {
        String value = System.getenv(varName);
        if (value == null || value.isBlank()) {
            // Already caught by the REQUIRED loop above; don't double-report.
            return;
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            missing.add(varName + " (must be at least " + MIN_SECRET_LENGTH + " characters)");
        }
    }
}
