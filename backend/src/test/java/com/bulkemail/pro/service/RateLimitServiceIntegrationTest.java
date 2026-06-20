package com.bulkemail.pro.service;

import com.bulkemail.pro.config.TestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for all Redis-backed rate-limiting services.
 * Runs against a real Redis 7 container via Testcontainers.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class RateLimitServiceIntegrationTest {

    @Autowired ApiRateLimiterService apiRateLimiter;
    @Autowired SmtpRateLimiterService smtpRateLimiter;
    @Autowired JwtDenylistService     jwtDenylist;
    @Autowired DistributedLockService lockService;
    @Autowired StringRedisTemplate    redis;

    @BeforeEach
    void cleanRedis() {
        // Flush only our key namespaces so parallel test suites aren't disrupted
        var keys = redis.keys("api:rate:test:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);

        keys = redis.keys("smtp:rate:999*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);

        keys = redis.keys("jwt:deny:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);

        keys = redis.keys("lock:test*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    // ── ApiRateLimiterService ────────────────────────────────────────

    @Test
    @DisplayName("allows requests within limit")
    void apiRateLimiter_allowsWithinLimit() {
        String bucket = "test:ip:127.0.0.1";
        for (int i = 0; i < 5; i++) {
            assertThat(apiRateLimiter.isAllowed(bucket, 5, 60)).isTrue();
        }
    }

    @Test
    @DisplayName("blocks requests exceeding limit")
    void apiRateLimiter_blocksAtLimit() {
        String bucket = "test:ip:10.0.0.1";
        for (int i = 0; i < 3; i++) {
            apiRateLimiter.isAllowed(bucket, 3, 60);
        }
        assertThat(apiRateLimiter.isAllowed(bucket, 3, 60)).isFalse();
    }

    @Test
    @DisplayName("remaining decrements with each request")
    void apiRateLimiter_remainingDecrementsCorrectly() {
        String bucket = "test:ip:192.168.0.1";
        assertThat(apiRateLimiter.remaining(bucket, 10, 60)).isEqualTo(10);
        apiRateLimiter.isAllowed(bucket, 10, 60);
        apiRateLimiter.isAllowed(bucket, 10, 60);
        assertThat(apiRateLimiter.remaining(bucket, 10, 60)).isEqualTo(8);
    }

    @Test
    @DisplayName("different buckets are isolated")
    void apiRateLimiter_bucketsAreIsolated() {
        String bucketA = "test:ip:1.1.1.1";
        String bucketB = "test:ip:2.2.2.2";
        for (int i = 0; i < 2; i++) apiRateLimiter.isAllowed(bucketA, 2, 60);

        assertThat(apiRateLimiter.isAllowed(bucketA, 2, 60)).isFalse();
        assertThat(apiRateLimiter.isAllowed(bucketB, 2, 60)).isTrue();
    }

    // ── SmtpRateLimiterService ───────────────────────────────────────

    @Test
    @DisplayName("canSend returns true when under both limits")
    void smtpRateLimiter_allowsWhenUnderLimit() {
        Long id = 999L;
        assertThat(smtpRateLimiter.canSend(id, 1000, 100)).isTrue();
    }

    @Test
    @DisplayName("recordSent increments daily and hourly counters")
    void smtpRateLimiter_recordSentIncrementsCounters() {
        Long id = 999L;
        smtpRateLimiter.recordSent(id);
        smtpRateLimiter.recordSent(id);
        assertThat(smtpRateLimiter.getDailyCount(id)).isEqualTo(2);
        assertThat(smtpRateLimiter.getHourlyCount(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("canSend blocks when daily limit reached")
    void smtpRateLimiter_blocksWhenDailyLimitReached() {
        Long id = 999L;
        smtpRateLimiter.recordSent(id);
        smtpRateLimiter.recordSent(id);
        assertThat(smtpRateLimiter.canSend(id, 2, 1000)).isFalse();
    }

    @Test
    @DisplayName("resetDaily zeroes the daily counter")
    void smtpRateLimiter_resetDailyWorks() {
        Long id = 999L;
        smtpRateLimiter.recordSent(id);
        smtpRateLimiter.resetDaily(id);
        assertThat(smtpRateLimiter.getDailyCount(id)).isZero();
    }

    // ── JwtDenylistService ───────────────────────────────────────────

    @Test
    @DisplayName("token not in denylist before logout")
    void jwtDenylist_tokenNotDeniedBeforeLogout() {
        assertThat(jwtDenylist.isDenied("some.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("denied token is detected immediately")
    void jwtDenylist_tokenDeniedAfterLogout() {
        String token = "header.payload.signature";
        jwtDenylist.deny(token, 3_600_000L); // 1 hour remaining
        assertThat(jwtDenylist.isDenied(token)).isTrue();
    }

    @Test
    @DisplayName("different tokens are independent in denylist")
    void jwtDenylist_differentTokensAreIndependent() {
        jwtDenylist.deny("token.one", 60_000L);
        assertThat(jwtDenylist.isDenied("token.two")).isFalse();
    }

    @Test
    @DisplayName("already-expired token (remainingMs=0) is not stored")
    void jwtDenylist_expiredTokenNotStored() {
        jwtDenylist.deny("expired.token", 0L);
        assertThat(jwtDenylist.isDenied("expired.token")).isFalse();
    }

    // ── DistributedLockService ───────────────────────────────────────

    @Test
    @DisplayName("lock is acquired when resource is free")
    void distributedLock_acquiresWhenFree() {
        String lockVal = lockService.newLockValue();
        assertThat(lockService.acquire("test:campaign:1", lockVal, 5_000)).isTrue();
        lockService.release("test:campaign:1", lockVal);
    }

    @Test
    @DisplayName("second acquisition fails while lock is held")
    void distributedLock_secondAcquisitionFails() {
        String v1 = lockService.newLockValue();
        String v2 = lockService.newLockValue();
        lockService.acquire("test:campaign:2", v1, 5_000);
        assertThat(lockService.acquire("test:campaign:2", v2, 5_000)).isFalse();
        lockService.release("test:campaign:2", v1);
    }

    @Test
    @DisplayName("release only succeeds for the owning value")
    void distributedLock_releaseRequiresOwnership() {
        String v1 = lockService.newLockValue();
        String v2 = lockService.newLockValue();
        lockService.acquire("test:campaign:3", v1, 5_000);
        assertThat(lockService.release("test:campaign:3", v2)).isFalse();
        assertThat(lockService.isLocked("test:campaign:3")).isTrue();
        lockService.release("test:campaign:3", v1);
    }

    @Test
    @DisplayName("isLocked reflects current state")
    void distributedLock_isLockedReflectsState() {
        String resource = "test:campaign:4";
        String lockVal  = lockService.newLockValue();
        assertThat(lockService.isLocked(resource)).isFalse();
        lockService.acquire(resource, lockVal, 5_000);
        assertThat(lockService.isLocked(resource)).isTrue();
        lockService.release(resource, lockVal);
        assertThat(lockService.isLocked(resource)).isFalse();
    }
}
