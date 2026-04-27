package com.bulkemail.pro.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        // Return a safe, readable message — don't expose stack traces to frontend
        String userMessage = friendlyMessage(ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", userMessage));
    }

    private String friendlyMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) return "An unexpected error occurred. Please try again.";
        if (msg.contains("Connection refused") || msg.contains("ECONNREFUSED"))
            return "Could not connect to the mail server. Please check your SMTP settings.";
        if (msg.contains("Authentication failed") || msg.contains("535"))
            return "SMTP authentication failed. Please check your username and password.";
        if (msg.contains("SSL") || msg.contains("TLS") || msg.contains("handshake"))
            return "SSL/TLS connection failed. Try changing the security type in your SMTP settings.";
        if (msg.contains("timeout") || msg.contains("timed out"))
            return "Connection timed out. The mail server may be unreachable or the port may be blocked.";
        if (msg.contains("Text must not be null"))
            return "Email body is empty. Please add HTML content to your campaign before sending.";
        if (msg.contains("No property") || msg.contains("could not"))
            return "A database configuration error occurred. Please contact support.";
        return msg;
    }
}
