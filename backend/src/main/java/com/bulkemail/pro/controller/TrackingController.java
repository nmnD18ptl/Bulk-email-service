package com.bulkemail.pro.controller;

import com.bulkemail.pro.service.ContactService;
import com.bulkemail.pro.service.EmailSenderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Base64;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TrackingController {

    private final EmailSenderService emailSenderService;
    private final ContactService contactService;

    // 1x1 transparent GIF
    private static final byte[] TRACKING_PIXEL = Base64.getDecoder().decode(
        "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    @GetMapping(value = "/track/open/{trackingId}", produces = MediaType.IMAGE_GIF_VALUE)
    public byte[] trackOpen(
            @PathVariable String trackingId,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        emailSenderService.recordOpen(trackingId, ip, userAgent);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        return TRACKING_PIXEL;
    }

    @GetMapping("/track/click/{trackingId}")
    public void trackClick(
            @PathVariable String trackingId,
            @RequestParam String url,
            HttpServletResponse response) throws IOException {

        String redirectUrl = emailSenderService.recordClick(trackingId, url);
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            redirectUrl = "https://example.com";
        }
        response.sendRedirect(redirectUrl);
    }

    // One-click unsubscribe for Gmail/Yahoo (RFC 8058)
    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<Void> unsubscribeOneClick(@PathVariable String token) {
        contactService.unsubscribe(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unsubscribe/{token}")
    public String unsubscribe(@PathVariable String token) {
        contactService.unsubscribe(token);
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Unsubscribed</title>
            <style>
              body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background: #f5f5f5; }
              .card { background: white; padding: 40px; border-radius: 8px; max-width: 500px; margin: 0 auto; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
              h1 { color: #2c3e50; }
              p { color: #666; }
            </style>
            </head>
            <body>
              <div class="card">
                <h1>✓ You've been unsubscribed</h1>
                <p>You have been successfully removed from our mailing list.</p>
                <p>You will no longer receive emails from us.</p>
              </div>
            </body>
            </html>
            """;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
