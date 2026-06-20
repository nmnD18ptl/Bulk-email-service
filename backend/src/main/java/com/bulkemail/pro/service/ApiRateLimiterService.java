package com.bulkemail.pro.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Sliding-window API rate limiter backed by a Redis sorted set.
 *
 * Algorithm (O(log N)):
 *  1. Remove all members with score < (now - windowMs)     → evict expired requests
 *  2. Count remaining members                               → current window count
 *  3. If count >= maxRequests → reject (return false)
 *  4. Otherwise ZADD current timestamp as both score and member → record this request
 *  5. Reset key TTL to windowSeconds                        → auto-cleanup idle keys
 *
 * Key pattern:  api:rate:{bucket}
 * Example buckets:
 *   login:{ip}           — brute-force protection (10/min per IP)
 *   org:{orgId}          — general org throughput (300/min)
 *   campaign:{orgId}     — campaign-send endpoint (10/min per org)
 */
@Service
public class ApiRateLimiterService {

    private static final String KEY_PREFIX = "api:rate:";

    private final RedisTemplate<String, Object> redisTemplate;

    public ApiRateLimiterService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param bucket       rate-limit bucket identifier (e.g. "login:192.168.1.1")
     * @param maxRequests  maximum allowed requests within the window
     * @param windowSeconds sliding window size in seconds
     * @return true if the request is allowed, false if the limit is exceeded
     */
    public boolean isAllowed(String bucket, int maxRequests, long windowSeconds) {
        String key     = KEY_PREFIX + bucket;
        long   now     = System.currentTimeMillis();
        long   cutoff  = now - (windowSeconds * 1_000);

        // Evict timestamps outside the sliding window
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);

        // Count requests currently in the window
        Long count = redisTemplate.opsForZSet().zCard(key);
        if (count != null && count >= maxRequests) {
            return false;
        }

        // Record this request — use "now:nano" as member to avoid duplicate-score collisions
        String member = now + ":" + System.nanoTime();
        redisTemplate.opsForZSet().add(key, member, now);
        redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        return true;
    }

    /**
     * Returns how many requests remain in the current window for the given bucket.
     * Used to populate X-RateLimit-Remaining response headers.
     */
    public long remaining(String bucket, int maxRequests, long windowSeconds) {
        String key    = KEY_PREFIX + bucket;
        long   cutoff = System.currentTimeMillis() - (windowSeconds * 1_000);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
        Long count = redisTemplate.opsForZSet().zCard(key);
        long used = count != null ? count : 0L;
        return Math.max(0, maxRequests - used);
    }
}
