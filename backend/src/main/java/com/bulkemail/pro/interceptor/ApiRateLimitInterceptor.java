package com.bulkemail.pro.interceptor;

import com.bulkemail.pro.service.ApiRateLimiterService;
import com.bulkemail.pro.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies sliding-window rate limits before each request reaches a controller.
 *
 * Limits enforced:
 *  ┌──────────────────────────────────────────┬───────────┬────────┐
 *  │ Endpoint pattern                         │ Max reqs  │ Window │
 *  ├──────────────────────────────────────────┼───────────┼────────┤
 *  │ /api/auth/login, /api/auth/register      │ 10 / IP   │ 60 s   │
 *  │ /api/campaigns/{id}/send                 │ 5  / org  │ 60 s   │
 *  │ All other /api/** (per org)              │ 300/ org  │ 60 s   │
 *  └──────────────────────────────────────────┴───────────┴────────┘
 *
 * Rejected requests receive HTTP 429 with a JSON body and standard
 * rate-limit headers (X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After).
 */
@Component
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private static final int  AUTH_LIMIT        = 10;
    private static final int  CAMPAIGN_SEND_LIMIT = 5;
    private static final int  ORG_LIMIT         = 300;
    private static final long WINDOW_SECONDS    = 60L;

    private final ApiRateLimiterService rateLimiter;

    public ApiRateLimitInterceptor(ApiRateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();

        // ── 1. Auth endpoints — protect by IP (no JWT yet at this point) ──
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
            String ip     = extractClientIp(request);
            String bucket = "login:" + ip;
            if (!rateLimiter.isAllowed(bucket, AUTH_LIMIT, WINDOW_SECONDS)) {
                return reject(response, AUTH_LIMIT,
                        rateLimiter.remaining(bucket, AUTH_LIMIT, WINDOW_SECONDS),
                        "Too many login attempts. Please wait 60 seconds.");
            }
            return true;
        }

        // ── 2. Skip non-API paths (actuator, tracking, unsubscribe) ──
        if (!path.startsWith("/api/")) return true;

        Long orgId = TenantContext.getOrganizationId();
        if (orgId == null) return true; // unauthenticated — Spring Security handles it

        // ── 3. Campaign send — tighter limit to prevent accidental double-sends ──
        if (path.matches("/api/campaigns/\\d+/send")) {
            String bucket = "campaign-send:" + orgId;
            if (!rateLimiter.isAllowed(bucket, CAMPAIGN_SEND_LIMIT, WINDOW_SECONDS)) {
                return reject(response, CAMPAIGN_SEND_LIMIT,
                        rateLimiter.remaining(bucket, CAMPAIGN_SEND_LIMIT, WINDOW_SECONDS),
                        "Campaign send rate limit exceeded. Max 5 sends per minute per organisation.");
            }
        }

        // ── 4. General org-level limit ──
        String orgBucket = "org:" + orgId;
        if (!rateLimiter.isAllowed(orgBucket, ORG_LIMIT, WINDOW_SECONDS)) {
            return reject(response, ORG_LIMIT,
                    rateLimiter.remaining(orgBucket, ORG_LIMIT, WINDOW_SECONDS),
                    "Rate limit exceeded. Max 300 requests per minute.");
        }

        // Attach remaining count to response headers for observability
        long rem = rateLimiter.remaining(orgBucket, ORG_LIMIT, WINDOW_SECONDS);
        response.setHeader("X-RateLimit-Limit",     String.valueOf(ORG_LIMIT));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rem));
        response.setHeader("X-RateLimit-Reset",
                String.valueOf(System.currentTimeMillis() / 1_000 + WINDOW_SECONDS));

        return true;
    }

    // ── Private helpers ──────────────────────────────────────────────

    private boolean reject(HttpServletResponse response, int limit, long remaining,
                           String message) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-RateLimit-Limit",     String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("Retry-After",            String.valueOf(WINDOW_SECONDS));
        response.getWriter().write(
                "{\"error\":\"" + message + "\","
                + "\"retryAfterSeconds\":" + WINDOW_SECONDS + "}");
        return false;
    }

    /**
     * Reads the real client IP, honoring X-Forwarded-For when behind a proxy.
     * Takes only the first (leftmost) IP to prevent header spoofing.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
