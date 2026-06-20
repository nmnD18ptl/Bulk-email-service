package com.bulkemail.pro.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * JWT denylist backed by Redis.
 *
 * On logout, the token's SHA-256 hash is stored in Redis with a TTL equal
 * to the token's remaining lifetime.  Every request in JwtAuthFilter checks
 * this before trusting the token.
 *
 * We store the hash (not the raw token) so that even if Redis is dumped,
 * no usable credentials are exposed.
 *
 * Key pattern:  jwt:deny:{sha256hex}
 */
@Service
public class JwtDenylistService {

    private static final String PREFIX = "jwt:deny:";

    private final StringRedisTemplate redis;

    public JwtDenylistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Adds a token to the denylist.
     *
     * @param token        raw JWT
     * @param remainingMs  milliseconds until the token expires naturally;
     *                     the Redis key will expire at the same moment,
     *                     so no storage leak occurs.
     */
    public void deny(String token, long remainingMs) {
        if (remainingMs <= 0) return; // already expired — nothing to store
        long ttlSeconds = Math.max(1, remainingMs / 1_000);
        redis.opsForValue().set(key(token), "1", ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Returns true when the token has been explicitly revoked (logged out).
     * Called on every authenticated request before the token claims are trusted.
     */
    public boolean isDenied(String token) {
        return Boolean.TRUE.equals(redis.hasKey(key(token)));
    }

    // ── Private helpers ──────────────────────────────────────────────

    private String key(String token) {
        return PREFIX + sha256Hex(token);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md   = MessageDigest.getInstance("SHA-256");
            byte[]        hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
