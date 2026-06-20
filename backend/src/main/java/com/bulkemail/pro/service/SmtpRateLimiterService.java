package com.bulkemail.pro.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Distributed SMTP rate limiter backed by Redis.
 *
 * Replaces the in-memory sentToday / sentThisHour fields on SmtpConfig,
 * which break under multiple app instances (each instance held its own
 * counter and never saw the other instances' sends).
 *
 * Keys:
 *   smtp:rate:{id}:daily:{yyyy-MM-dd}     — auto-expires at midnight
 *   smtp:rate:{id}:hourly:{yyyy-MM-ddTHH} — auto-expires at the hour boundary
 *
 * Atomicity: Redis INCR is atomic by design — no race condition possible.
 */
@Service
public class SmtpRateLimiterService {

    private static final String DAILY_KEY  = "smtp:rate:%d:daily:%s";
    private static final String HOURLY_KEY = "smtp:rate:%d:hourly:%s";

    private final StringRedisTemplate redis;

    public SmtpRateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Returns true when the config is below both daily and hourly limits. */
    public boolean canSend(Long smtpConfigId, int dailyLimit, int hourlyLimit) {
        long daily  = getCount(dailyKey(smtpConfigId));
        long hourly = getCount(hourlyKey(smtpConfigId));
        return daily < dailyLimit && hourly < hourlyLimit;
    }

    /**
     * Atomically increments both counters and sets their TTL on first use.
     * Safe to call from multiple threads / instances simultaneously.
     */
    public void recordSent(Long smtpConfigId) {
        String dk = dailyKey(smtpConfigId);
        Long dailyCount = redis.opsForValue().increment(dk);
        if (Long.valueOf(1).equals(dailyCount)) {
            redis.expire(dk, secondsUntilEndOfDay(), TimeUnit.SECONDS);
        }

        String hk = hourlyKey(smtpConfigId);
        Long hourlyCount = redis.opsForValue().increment(hk);
        if (Long.valueOf(1).equals(hourlyCount)) {
            redis.expire(hk, secondsUntilEndOfHour(), TimeUnit.SECONDS);
        }
    }

    public long getDailyCount(Long smtpConfigId) {
        return getCount(dailyKey(smtpConfigId));
    }

    public long getHourlyCount(Long smtpConfigId) {
        return getCount(hourlyKey(smtpConfigId));
    }

    /** Resets daily counter (e.g. after provider quota reset). */
    public void resetDaily(Long smtpConfigId) {
        redis.delete(dailyKey(smtpConfigId));
    }

    // ── Private helpers ──────────────────────────────────────────────

    private long getCount(String key) {
        String val = redis.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0L;
    }

    private String dailyKey(Long id) {
        return String.format(DAILY_KEY, id, LocalDate.now());
    }

    private String hourlyKey(Long id) {
        return String.format(HOURLY_KEY, id,
                LocalDateTime.now().truncatedTo(ChronoUnit.HOURS));
    }

    private long secondsUntilEndOfDay() {
        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, endOfDay).getSeconds();
    }

    private long secondsUntilEndOfHour() {
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime endOfHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        return Duration.between(now, endOfHour).getSeconds();
    }
}
