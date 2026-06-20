package com.bulkemail.pro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.5 Phase 1 — Mailpit integration tests.
 *
 * Verifies the email infrastructure against a live Mailpit SMTP server:
 *   - Email delivery
 *   - Merge tag rendering (pre-personalized content preserved through SMTP)
 *   - Tracking pixel injection (pixel URL survives MIME encoding)
 *   - Click tracking links (redirector URLs preserved in HTML)
 *   - Unsubscribe links (present in HTML body and List-Unsubscribe header)
 *   - RFC-compliant headers (List-Unsubscribe, List-Unsubscribe-Post, Precedence,
 *     Feedback-ID, Message-ID, X-Entity-Ref-ID; X-Mailer absent)
 *
 * These tests do not require a database — they test the SMTP layer in isolation,
 * mirroring exactly what EmailSenderService produces when it builds a MimeMessage.
 */
@Testcontainers
class MailpitEmailDeliveryIntegrationTest {

    @Container
    @SuppressWarnings("resource") // lifecycle managed by @Testcontainers
    static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
                    .withExposedPorts(1025, 8025);

    private static final String FROM_EMAIL    = "sender@bulkemail-test.local";
    private static final String FROM_NAME     = "Bulk Email Test";
    private static final String TO_EMAIL      = "recipient@test.local";
    private static final String TRACKING_BASE = "http://localhost:8080";
    private static final String FROM_DOMAIN   = "bulkemail-test.local";

    private JavaMailSenderImpl mailSender;
    private final RestTemplate http   = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mailSender = new JavaMailSenderImpl();
        mailSender.setHost(MAILPIT.getHost());
        mailSender.setPort(MAILPIT.getMappedPort(1025));
        mailSender.getJavaMailProperties().setProperty("mail.transport.protocol", "smtp");
        mailSender.getJavaMailProperties().setProperty("mail.smtp.auth", "false");
        mailSender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "false");

        // Start each test with a clean inbox
        http.delete(apiUrl("/api/v1/messages"));
    }

    // ── Email delivery ───────────────────────────────────────────────────────

    @Test
    @DisplayName("email is delivered to Mailpit inbox")
    void emailDelivery_messageArrivesInMailpit() throws Exception {
        sendEmail("Delivery Test", "<p>Hello from Bulk Email Pro.</p>", null);

        JsonNode messages = pollMessages(1);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).path("Subject").asText()).isEqualTo("Delivery Test");
        assertThat(messages.get(0).path("To").get(0).path("Address").asText()).isEqualTo(TO_EMAIL);
        assertThat(messages.get(0).path("From").path("Address").asText()).isEqualTo(FROM_EMAIL);
    }

    // ── Merge tag rendering ──────────────────────────────────────────────────

    @Test
    @DisplayName("merge tags are rendered in subject and HTML body")
    void mergeTags_renderedInSubjectAndBody() throws Exception {
        // Simulate pre-rendered merge tags as BatchProcessor produces them
        String subject = "Hello, John Doe!";
        String html    = "<p>Dear John Doe,</p>"
                       + "<p>Your email address is: john.doe@example.com</p>"
                       + "<p>Company: Acme Corp</p>";

        sendEmail(subject, html, null);

        JsonNode messages = pollMessages(1);
        String   msgId    = messages.get(0).path("ID").asText();
        JsonNode msg      = getMessage(msgId);

        assertThat(msg.path("Subject").asText()).isEqualTo(subject);
        assertThat(msg.path("HTML").asText())
                .contains("John Doe")
                .contains("john.doe@example.com")
                .contains("Acme Corp");
    }

    // ── Tracking pixel ───────────────────────────────────────────────────────

    @Test
    @DisplayName("tracking pixel URL is preserved through SMTP")
    void trackingPixel_urlPreservedInHtmlBody() throws Exception {
        String trackingId = UUID.randomUUID().toString();
        String pixelUrl   = TRACKING_BASE + "/tracking/" + trackingId + "/pixel";
        String html       = "<p>Hello</p>"
                          + "<img src=\"" + pixelUrl
                          + "\" width=\"1\" height=\"1\" border=\"0\" alt=\"\" />";

        sendEmail("Tracking Pixel Test", html, null);

        JsonNode messages = pollMessages(1);
        String   msgId    = messages.get(0).path("ID").asText();
        JsonNode msg      = getMessage(msgId);

        assertThat(msg.path("HTML").asText()).contains(pixelUrl);
    }

    // ── Click tracking ───────────────────────────────────────────────────────

    @Test
    @DisplayName("click tracking redirect URLs are preserved in HTML body")
    void clickTracking_redirectUrlPreservedInHtmlBody() throws Exception {
        String trackingId = UUID.randomUUID().toString();
        String targetUrl  = "https://example.com/landing-page";
        String clickUrl   = TRACKING_BASE + "/tracking/" + trackingId + "/click?url="
                          + URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
        String html       = "<p><a href=\"" + clickUrl + "\">Visit our site</a></p>";

        sendEmail("Click Tracking Test", html, null);

        JsonNode messages = pollMessages(1);
        String   msgId    = messages.get(0).path("ID").asText();
        JsonNode msg      = getMessage(msgId);

        assertThat(msg.path("HTML").asText()).contains(clickUrl);
    }

    // ── Unsubscribe ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("unsubscribe link present in HTML body and List-Unsubscribe header")
    void unsubscribeLink_inHtmlBodyAndListUnsubscribeHeader() throws Exception {
        String unsubToken = UUID.randomUUID().toString();
        String unsubUrl   = TRACKING_BASE + "/unsubscribe/" + unsubToken;
        String html       = "<p>Not interested? <a href=\"" + unsubUrl + "\">Unsubscribe</a></p>";

        sendEmail("Unsubscribe Test", html, unsubUrl);

        JsonNode messages = pollMessages(1);
        String   msgId    = messages.get(0).path("ID").asText();
        JsonNode msg      = getMessage(msgId);

        assertThat(msg.path("HTML").asText()).contains(unsubUrl);

        assertThat(msg.path("Headers").has("List-Unsubscribe")).isTrue();
        assertThat(msg.path("Headers").path("List-Unsubscribe").get(0).asText())
                .contains(unsubUrl);
    }

    // ── RFC headers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("all RFC-compliant headers are present on outbound email")
    void rfcHeaders_allRequiredHeadersPresent() throws Exception {
        String unsubUrl = TRACKING_BASE + "/unsubscribe/" + UUID.randomUUID();
        sendEmail("RFC Headers Test", "<p>Test body</p>", unsubUrl);

        JsonNode messages = pollMessages(1);
        String   msgId    = messages.get(0).path("ID").asText();
        JsonNode msg      = getMessage(msgId);
        JsonNode hdr      = msg.path("Headers");

        // List-Unsubscribe: must contain both mailto: and HTTP unsubscribe URL
        assertThat(hdr.has("List-Unsubscribe")).isTrue();
        String listUnsub = hdr.path("List-Unsubscribe").get(0).asText();
        assertThat(listUnsub).contains("mailto:");
        assertThat(listUnsub).contains(unsubUrl);

        // One-click unsubscribe — required by Gmail/Yahoo bulk sender policy
        assertThat(hdr.has("List-Unsubscribe-Post")).isTrue();
        assertThat(hdr.path("List-Unsubscribe-Post").get(0).asText())
                .isEqualTo("List-Unsubscribe=One-Click");

        // Precedence: bulk
        assertThat(hdr.has("Precedence")).isTrue();
        assertThat(hdr.path("Precedence").get(0).asText()).isEqualTo("bulk");

        // Feedback-ID for ISP FBL loop correlation
        assertThat(hdr.has("Feedback-ID")).isTrue();
        assertThat(hdr.path("Feedback-ID").get(0).asText()).isNotBlank();

        // Message-ID in RFC 5322 angle-bracket format: <localpart@domain>
        assertThat(hdr.has("Message-ID")).isTrue();
        assertThat(hdr.path("Message-ID").get(0).asText()).matches("<[^>]+@[^>]+>");

        // X-Entity-Ref-ID for per-email deduplication
        assertThat(hdr.has("X-Entity-Ref-ID")).isTrue();

        // X-Mailer must be absent — prevents MUA fingerprinting
        assertThat(hdr.has("X-Mailer")).isFalse();
    }

    @Test
    @DisplayName("Message-ID is unique for each email")
    void rfcHeaders_messageIdIsUniquePerEmail() throws Exception {
        sendEmail("First Email",  "<p>First</p>",  null);
        sendEmail("Second Email", "<p>Second</p>", null);

        JsonNode messages = pollMessages(2);

        String id1 = getMessage(messages.get(0).path("ID").asText())
                .path("Headers").path("Message-ID").get(0).asText();
        String id2 = getMessage(messages.get(1).path("ID").asText())
                .path("Headers").path("Message-ID").get(0).asText();

        assertThat(id1).isNotEqualTo(id2);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds and sends a MimeMessage that mirrors what EmailSenderService produces.
     * The {@code unsubscribeUrl} parameter may be {@code null}; a placeholder is used.
     */
    private void sendEmail(String subject, String htmlBody, String unsubscribeUrl) throws Exception {
        MimeMessage        message = mailSender.createMimeMessage();
        MimeMessageHelper  helper  = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(FROM_EMAIL, FROM_NAME);
        helper.setTo(TO_EMAIL);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        String replyTo  = FROM_EMAIL;
        String unsubUrl = unsubscribeUrl != null
                ? unsubscribeUrl
                : TRACKING_BASE + "/unsubscribe/" + UUID.randomUUID();

        // Mirror EmailSenderService header logic exactly
        message.setHeader("List-Unsubscribe",
                "<mailto:" + replyTo + "?subject=unsubscribe>, <" + unsubUrl + ">");
        message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        message.setHeader("Precedence", "bulk");
        message.setHeader("Feedback-ID", "1:" + FROM_DOMAIN + ":campaign:" + FROM_DOMAIN);
        message.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());
        message.setHeader("Message-ID", "<" + UUID.randomUUID() + "@" + FROM_DOMAIN + ">");
        message.removeHeader("X-Mailer");

        mailSender.send(message);
    }

    /**
     * Polls Mailpit's message list until {@code expectedCount} messages have arrived,
     * or throws {@link AssertionError} after a 10-second timeout.
     */
    private JsonNode pollMessages(int expectedCount) throws Exception {
        String url = apiUrl("/api/v1/messages");
        for (int attempt = 0; attempt < 20; attempt++) {
            String   body = http.getForObject(url, String.class);
            JsonNode root = mapper.readTree(body);
            JsonNode msgs = root.path("messages");
            if (!msgs.isMissingNode() && msgs.size() >= expectedCount) {
                return msgs;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for " + expectedCount + " message(s) in Mailpit");
    }

    private JsonNode getMessage(String mailpitMessageId) throws Exception {
        String json = http.getForObject(apiUrl("/api/v1/message/" + mailpitMessageId), String.class);
        return mapper.readTree(json);
    }

    private String apiUrl(String path) {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path;
    }
}
