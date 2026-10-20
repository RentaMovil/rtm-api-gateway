package com.rentamovil.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        for (int i = 1; i <= 10; i++) {
            assertThat(rateLimiter.tryConsume("ip:1", 10, Duration.ofMinutes(5)).allowed()).isTrue();
        }

        RateLimiter.Result eleventh = rateLimiter.tryConsume("ip:1", 10, Duration.ofMinutes(5));

        assertThat(eleventh.allowed()).isFalse();
        assertThat(eleventh.retryAfterSeconds()).isEqualTo(300);
    }

    @Test
    void theCounterResetsWhenTheWindowEnds() {
        for (int i = 0; i < 11; i++) {
            rateLimiter.tryConsume("ip:1", 10, Duration.ofMinutes(5));
        }

        clock.advance(Duration.ofMinutes(5));

        assertThat(rateLimiter.tryConsume("ip:1", 10, Duration.ofMinutes(5)).allowed()).isTrue();
    }

    @Test
    void eachKeyHasItsOwnCounter() {
        for (int i = 0; i < 11; i++) {
            rateLimiter.tryConsume("ip:1", 10, Duration.ofMinutes(5));
        }

        assertThat(rateLimiter.tryConsume("ip:2", 10, Duration.ofMinutes(5)).allowed()).isTrue();
    }

    @Test
    void cleanupRemovesExpiredWindows() {
        rateLimiter.tryConsume("ip:1", 10, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));

        rateLimiter.removeExpiredWindows();

        assertThat(rateLimiter.trackedKeys()).isZero();
    }

    // A clock that can be advanced by hand so the test doesn't wait 5 real minutes
    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
