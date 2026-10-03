package com.antonk404.hhbot.service.util;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.store.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();

    private RateLimiter at(String moscowTime) {
        Instant instant = java.time.LocalDateTime.parse(moscowTime).atZone(MOSCOW).toInstant();
        return new RateLimiter(store, Clock.fixed(instant, MOSCOW), MOSCOW, 9, 21);
    }

    private static ResponseRule rule(long id, int limit) {
        ResponseRule rule = new ResponseRule(1L, "java");
        ReflectionTestUtils.setField(rule, "id", id);
        rule.setDailyLimit(limit);
        return rule;
    }

    @Test
    void exhaustedOnceLimitIsReached() {
        RateLimiter limiter = at("2026-10-03T12:00:00");
        ResponseRule rule = rule(7, 2);

        assertFalse(limiter.exhausted(rule));
        limiter.recordSent(rule);
        assertFalse(limiter.exhausted(rule));
        limiter.recordSent(rule);
        assertTrue(limiter.exhausted(rule));
        assertEquals(2, limiter.sentToday(rule));
    }

    @Test
    void counterIsPerRuleAndPerDay() {
        ResponseRule rule = rule(7, 1);
        at("2026-10-03T12:00:00").recordSent(rule);

        assertFalse(at("2026-10-03T12:00:00").exhausted(rule(8, 1)));
        assertFalse(at("2026-10-04T09:00:00").exhausted(rule));
    }

    @Test
    void workingHoursAreHalfOpen() {
        assertFalse(at("2026-10-03T08:59:00").withinWorkingHours());
        assertTrue(at("2026-10-03T09:00:00").withinWorkingHours());
        assertTrue(at("2026-10-03T20:59:00").withinWorkingHours());
        assertFalse(at("2026-10-03T21:00:00").withinWorkingHours());
    }

    @Test
    void stopLastsExactlyUntilLocalMidnight() {
        RateLimiter limiter = at("2026-10-03T20:30:00");

        assertFalse(limiter.isStopped(5L));
        limiter.stopUntilTomorrow(5L, "captcha");

        assertTrue(limiter.isStopped(5L));
        assertFalse(limiter.isStopped(6L));
        assertEquals(Duration.ofMinutes(210), store.ttls.get("stop:5"));
    }
}
