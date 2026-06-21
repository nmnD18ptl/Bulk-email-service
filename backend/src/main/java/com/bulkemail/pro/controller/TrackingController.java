package com.bulkemail.pro.controller;

import com.bulkemail.pro.service.ContactService;
import com.bulkemail.pro.service.EmailSenderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.tracking.base-url}")
    private String trackingBaseUrl;

    // 1x1 transparent GIF
    private static final byte[] TRACKING_PIXEL = Base64.getDecoder().decode(
        "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    // ── Open tracking ─────────────────────────────────────────────────────────

    @GetMapping(value = "/track/open/{trackingId}", produces = MediaType.IMAGE_GIF_VALUE)
    public byte[] trackOpen(
            @PathVariable String trackingId,
            HttpServletRequest request,
            HttpServletResponse response) {

        emailSenderService.recordOpen(trackingId, getClientIp(request), request.getHeader("User-Agent"));
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        return TRACKING_PIXEL;
    }

    // ── Click tracking ────────────────────────────────────────────────────────

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

    // ── Unsubscribe — RFC 8058 one-click (Gmail / Yahoo header button) ────────

    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<Void> unsubscribeOneClick(
            @PathVariable String token,
            @RequestParam(required = false) Long c) {

        contactService.unsubscribe(token);
        if (c != null) {
            emailSenderService.recordUnsubscribe(token, c, null);
        }
        return ResponseEntity.ok().build();
    }

    // ── Unsubscribe — link click (recipient clicks link in email body) ─────────

    @GetMapping("/unsubscribe/{token}")
    public String unsubscribe(
            @PathVariable String token,
            @RequestParam(required = false) Long c,
            HttpServletRequest request) {

        String ip = getClientIp(request);

        // 1. Mark UNSUBSCRIBED + add to suppression list
        var contactOpt = contactService.unsubscribe(token);

        // 2. Update campaign analytics (only when ?c= is present — older emails won't have it)
        if (c != null && contactOpt.isPresent()) {
            emailSenderService.recordUnsubscribe(token, c, ip);
        }

        // 3. Render professional preference centre
        String email = contactOpt.map(ct -> ct.getEmail()).orElse("your email");
        String resubscribeUrl = trackingBaseUrl + "/unsubscribe/" + token + "/resubscribe";

        return preferenceCentrePage(email, resubscribeUrl, contactOpt.isPresent());
    }

    // ── Re-subscribe (undo accidental unsubscribe) ────────────────────────────

    @GetMapping("/unsubscribe/{token}/resubscribe")
    public String resubscribe(@PathVariable String token) {
        var contactOpt = contactService.resubscribe(token);
        String email = contactOpt.map(ct -> ct.getEmail()).orElse("your email");
        return resubscribedPage(email, contactOpt.isPresent());
    }

    // ── Test-mode placeholder (used by test email sends) ─────────────────────

    @GetMapping("/unsubscribe/test-mode")
    public String unsubscribeTestMode() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Test Email</title>
              <style>
                *{margin:0;padding:0;box-sizing:border-box}
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                     background:#F3F4F6;min-height:100vh;display:flex;
                     align-items:center;justify-content:center;padding:20px}
                .card{background:white;border-radius:16px;padding:48px;max-width:480px;
                      width:100%;box-shadow:0 4px 24px rgba(0,0,0,.08);text-align:center}
                .icon{font-size:48px;margin-bottom:20px}
                .badge{display:inline-block;background:#FEF3C7;color:#92400E;
                       font-size:12px;font-weight:700;padding:4px 12px;
                       border-radius:100px;letter-spacing:.4px;margin-bottom:16px}
                h1{font-size:20px;color:#111827;margin-bottom:12px;font-weight:700}
                p{color:#6B7280;font-size:15px;line-height:1.6}
              </style>
            </head>
            <body>
              <div class="card">
                <div class="icon">🧪</div>
                <div class="badge">TEST EMAIL</div>
                <h1>Unsubscribe link is inactive</h1>
                <p>This was a test email. The unsubscribe link is only active when
                   sending to real recipients in a live campaign.</p>
              </div>
            </body>
            </html>
            """;
    }

    // ── HTML pages ────────────────────────────────────────────────────────────

    private String preferenceCentrePage(String email, String resubscribeUrl, boolean found) {
        String icon    = found ? "✅" : "⚠️";
        String heading = found ? "You've been unsubscribed" : "Link not recognised";
        String body    = found
            ? "The address <strong>" + escHtml(email) + "</strong> has been removed "
              + "from our mailing list. You will not receive any further marketing emails."
            : "We couldn't find an active subscription for this link. "
              + "It may have already been used or the link may be invalid.";
        String undo = found
            ? "<hr class='div'>"
              + "<p class='sm'>Unsubscribed by mistake?&nbsp;"
              + "<a href='" + escHtml(resubscribeUrl) + "'>Click here to re-subscribe</a></p>"
            : "";

        return "<!DOCTYPE html><html lang='en'>"
            + "<head><meta charset='UTF-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Unsubscribed</title>"
            + "<style>"
            + "*{margin:0;padding:0;box-sizing:border-box}"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
            + "background:#F3F4F6;min-height:100vh;display:flex;"
            + "align-items:center;justify-content:center;padding:20px}"
            + ".card{background:white;border-radius:16px;padding:48px 40px;"
            + "max-width:500px;width:100%;box-shadow:0 4px 24px rgba(0,0,0,.08);text-align:center}"
            + ".icon{font-size:52px;margin-bottom:20px}"
            + "h1{font-size:22px;color:#111827;margin-bottom:16px;font-weight:700}"
            + "p{color:#6B7280;font-size:15px;line-height:1.7}"
            + "strong{color:#111827}"
            + ".div{border:none;border-top:1px solid #F3F4F6;margin:24px 0}"
            + ".sm{font-size:14px;color:#9CA3AF}"
            + ".sm a{color:#3B82F6;text-decoration:none;font-weight:500}"
            + ".sm a:hover{text-decoration:underline}"
            + "</style></head>"
            + "<body><div class='card'>"
            + "<div class='icon'>" + icon + "</div>"
            + "<h1>" + heading + "</h1>"
            + "<p>" + body + "</p>"
            + undo
            + "</div></body></html>";
    }

    private String resubscribedPage(String email, boolean found) {
        String icon    = found ? "💙" : "⚠️";
        String heading = found ? "You're re-subscribed!" : "Link not recognised";
        String body    = found
            ? "Welcome back! <strong>" + escHtml(email) + "</strong> "
              + "has been re-added to our mailing list."
            : "We couldn't find a subscription linked to this address.";

        return "<!DOCTYPE html><html lang='en'>"
            + "<head><meta charset='UTF-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Re-subscribed</title>"
            + "<style>"
            + "*{margin:0;padding:0;box-sizing:border-box}"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
            + "background:#F3F4F6;min-height:100vh;display:flex;"
            + "align-items:center;justify-content:center;padding:20px}"
            + ".card{background:white;border-radius:16px;padding:48px 40px;"
            + "max-width:500px;width:100%;box-shadow:0 4px 24px rgba(0,0,0,.08);text-align:center}"
            + ".icon{font-size:52px;margin-bottom:20px}"
            + "h1{font-size:22px;color:#111827;margin-bottom:16px;font-weight:700}"
            + "p{color:#6B7280;font-size:15px;line-height:1.7}"
            + "strong{color:#111827}"
            + "</style></head>"
            + "<body><div class='card'>"
            + "<div class='icon'>" + icon + "</div>"
            + "<h1>" + heading + "</h1>"
            + "<p>" + body + "</p>"
            + "</div></body></html>";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /** Minimal HTML escaping to prevent XSS from email addresses in page output. */
    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }
}
