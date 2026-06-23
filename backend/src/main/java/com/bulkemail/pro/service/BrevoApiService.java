package com.bulkemail.pro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class BrevoApiService {

    private static final String BASE_URL = "https://api.brevo.com/v3";
    private final RestTemplate restTemplate = new RestTemplate();

    /** Returns null on success, or the error message string on failure. */
    public String testApiKey(String apiKey) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                BASE_URL + "/account",
                HttpMethod.GET,
                new HttpEntity<>(headers(apiKey)),
                Map.class
            );
            if (resp.getStatusCode().is2xxSuccessful()) return null;
            return "Unexpected status: " + resp.getStatusCode();
        } catch (HttpClientErrorException e) {
            return "API key rejected (" + e.getStatusCode() + "): " + e.getResponseBodyAsString();
        } catch (Exception e) {
            String msg = e.getMessage();
            log.error("Brevo API key test failed: {}", msg);
            return msg != null ? msg : e.getClass().getSimpleName();
        }
    }

    /**
     * Sends a transactional email via Brevo REST API (HTTPS / port 443).
     * Returns the Brevo messageId on success, throws RuntimeException on failure.
     */
    public String sendEmail(String apiKey,
                            String fromEmail, String fromName,
                            String toEmail,   String toName,
                            String subject,
                            String htmlContent, String textContent,
                            Map<String, String> customHeaders) {

        HttpHeaders httpHeaders = headers(apiKey);
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of(
            "name",  fromName != null && !fromName.isBlank() ? fromName : fromEmail,
            "email", fromEmail
        ));
        body.put("to", List.of(Map.of(
            "email", toEmail,
            "name",  toName  != null && !toName.isBlank()  ? toName  : toEmail
        )));
        body.put("subject", subject);

        if (htmlContent != null && !htmlContent.isBlank()) body.put("htmlContent", htmlContent);
        if (textContent != null && !textContent.isBlank()) body.put("textContent", textContent);
        if (customHeaders != null && !customHeaders.isEmpty()) body.put("headers", customHeaders);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                BASE_URL + "/smtp/email",
                HttpMethod.POST,
                new HttpEntity<>(body, httpHeaders),
                Map.class
            );
            Object msgId = resp.getBody() != null ? resp.getBody().get("messageId") : null;
            return msgId != null ? msgId.toString() : "sent-" + UUID.randomUUID();

        } catch (HttpClientErrorException e) {
            String detail = e.getResponseBodyAsString();
            log.error("Brevo API send failed to {} — {}: {}", toEmail, e.getStatusCode(), detail);
            throw new RuntimeException("Brevo API " + e.getStatusCode() + ": " + detail);
        } catch (Exception e) {
            log.error("Brevo API send failed to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Brevo API error: " + e.getMessage(), e);
        }
    }

    private HttpHeaders headers(String apiKey) {
        HttpHeaders h = new HttpHeaders();
        h.set("api-key", apiKey);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }
}
