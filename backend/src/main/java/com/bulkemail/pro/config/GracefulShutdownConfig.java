package com.bulkemail.pro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Configures and orchestrates graceful shutdown of RabbitMQ consumers.
 *
 * Shutdown sequence on SIGTERM:
 *   1. Spring stops accepting new HTTP requests (server.shutdown=graceful in application.yml)
 *   2. Spring calls SmartLifecycle.stop() on all RabbitMQ listener containers
 *   3. Each container stops pulling new messages from the queue
 *   4. In-flight handlers have up to shutdownTimeout (60 s) to ack / nack
 *   5. After timeout — or when all handlers complete — connections close cleanly
 *   6. Spring context destroys remaining beans (datasource, Redis, etc.)
 *
 * The 60-second window matches spring.lifecycle.timeout-per-shutdown-phase so
 * the whole shutdown completes within Railway's default SIGKILL grace period (90 s).
 */
@Component
public class GracefulShutdownConfig {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownConfig.class);
    private static final long SHUTDOWN_TIMEOUT_MS = 60_000L;

    private final RabbitListenerEndpointRegistry listenerRegistry;

    public GracefulShutdownConfig(RabbitListenerEndpointRegistry listenerRegistry) {
        this.listenerRegistry = listenerRegistry;
    }

    /**
     * After the application is fully started, push the 60-second shutdown timeout
     * into every registered listener container.
     *
     * ApplicationReadyEvent fires after all @RabbitListener beans are registered,
     * so the registry is fully populated at this point.
     */
    @EventListener(ApplicationReadyEvent.class)
    void configureShutdownTimeout() {
        listenerRegistry.getListenerContainers().forEach(container -> {
            if (container instanceof AbstractMessageListenerContainer c) {
                c.setShutdownTimeout(SHUTDOWN_TIMEOUT_MS);
            }
        });
        log.info("RabbitMQ consumers configured for graceful shutdown (timeout: {}s)",
                SHUTDOWN_TIMEOUT_MS / 1_000);
    }

    @EventListener(ContextClosedEvent.class)
    void onContextClosed() {
        log.info("Graceful shutdown initiated — RabbitMQ consumers will complete "
                + "in-flight messages before stopping (timeout: {}s)",
                SHUTDOWN_TIMEOUT_MS / 1_000);
    }
}
