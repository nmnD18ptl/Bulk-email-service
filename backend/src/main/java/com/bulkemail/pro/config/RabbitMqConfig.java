package com.bulkemail.pro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for Bulk Email Pro.
 *
 * Exchange:  bulkemail.direct  (direct — routes by routing key)
 *
 * Queues / routing keys:
 * ┌───────────────────────────────────┬──────────────────────────┐
 * │ Queue                             │ Routing key              │
 * ├───────────────────────────────────┼──────────────────────────┤
 * │ campaign.trigger                  │ campaign.trigger         │
 * │ campaign.trigger.dlq              │ campaign.trigger.dlq     │
 * │ email.send                        │ email.send               │
 * │ email.send.dlq                    │ email.send.dlq           │
 * │ webhook.inbound                   │ webhook.inbound          │
 * │ webhook.inbound.dlq               │ webhook.inbound.dlq      │
 * └───────────────────────────────────┴──────────────────────────┘
 *
 * Messages that exhaust retries (max-attempts=3 in application.yml) are
 * nacked without requeue, causing RabbitMQ to route them to the DLQ.
 * DLQ messages are retained for 7 days for manual inspection / replay.
 */
@Configuration
public class RabbitMqConfig {

    // ── Exchange ─────────────────────────────────────────────────────

    public static final String EXCHANGE = "bulkemail.direct";

    // ── Routing keys / queue names (kept identical for clarity) ──────

    public static final String CAMPAIGN_TRIGGER_QUEUE  = "campaign.trigger";
    public static final String CAMPAIGN_TRIGGER_DLQ    = "campaign.trigger.dlq";

    public static final String EMAIL_SEND_QUEUE        = "email.send";
    public static final String EMAIL_SEND_DLQ          = "email.send.dlq";

    public static final String WEBHOOK_INBOUND_QUEUE   = "webhook.inbound";
    public static final String WEBHOOK_INBOUND_DLQ     = "webhook.inbound.dlq";

    // ── Exchange bean ─────────────────────────────────────────────────

    @Bean
    DirectExchange bulkEmailExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    // ── Campaign trigger ──────────────────────────────────────────────

    @Bean
    Queue campaignTriggerQueue() {
        return QueueBuilder.durable(CAMPAIGN_TRIGGER_QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CAMPAIGN_TRIGGER_DLQ)
                .build();
    }

    @Bean
    Queue campaignTriggerDlq() {
        return QueueBuilder.durable(CAMPAIGN_TRIGGER_DLQ)
                .withArgument("x-message-ttl", 604_800_000L) // 7 days
                .build();
    }

    @Bean
    Binding campaignTriggerBinding(Queue campaignTriggerQueue, DirectExchange bulkEmailExchange) {
        return BindingBuilder.bind(campaignTriggerQueue).to(bulkEmailExchange).with(CAMPAIGN_TRIGGER_QUEUE);
    }

    @Bean
    Binding campaignTriggerDlqBinding(Queue campaignTriggerDlq, DirectExchange bulkEmailExchange) {
        return BindingBuilder.bind(campaignTriggerDlq).to(bulkEmailExchange).with(CAMPAIGN_TRIGGER_DLQ);
    }

    // ── Individual email send ─────────────────────────────────────────

    @Bean
    Queue emailSendQueue() {
        return QueueBuilder.durable(EMAIL_SEND_QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", EMAIL_SEND_DLQ)
                .build();
    }

    @Bean
    Queue emailSendDlq() {
        return QueueBuilder.durable(EMAIL_SEND_DLQ)
                .withArgument("x-message-ttl", 604_800_000L)
                .build();
    }

    @Bean
    Binding emailSendBinding(Queue emailSendQueue, DirectExchange bulkEmailExchange) {
        return BindingBuilder.bind(emailSendQueue).to(bulkEmailExchange).with(EMAIL_SEND_QUEUE);
    }

    @Bean
    Binding emailSendDlqBinding(Queue emailSendDlq, DirectExchange bulkEmailExchange) {
        return BindingBuilder.bind(emailSendDlq).to(bulkEmailExchange).with(EMAIL_SEND_DLQ);
    }

    // ── Webhook inbound ───────────────────────────────────────────────

    @Bean
    Queue webhookInboundQueue() {
        return QueueBuilder.durable(WEBHOOK_INBOUND_QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", WEBHOOK_INBOUND_DLQ)
                .build();
    }

    @Bean
    Queue webhookInboundDlq() {
        return QueueBuilder.durable(WEBHOOK_INBOUND_DLQ)
                .withArgument("x-message-ttl", 604_800_000L)
                .build();
    }

    @Bean
    Binding webhookInboundBinding(Queue webhookInboundQueue, DirectExchange bulkEmailExchange) {
        return BindingBuilder.bind(webhookInboundQueue).to(bulkEmailExchange).with(WEBHOOK_INBOUND_QUEUE);
    }

    @Bean
    Binding webhookInboundDlqBinding(Queue webhookInboundDlq, DirectExchange bulkEmailExchange) {
        return BindingBuilder.bind(webhookInboundDlq).to(bulkEmailExchange).with(WEBHOOK_INBOUND_DLQ);
    }

    // ── JSON message converter ────────────────────────────────────────

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setExchange(EXCHANGE);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(5);
        return factory;
    }
}
