package com.antonk404.hhbot.service.util;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.store.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

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
        limiter.stopUntilTomorrow(5L, RateLimiter.Stop.CAPTCHA);

        assertTrue(limiter.isStopped(5L));
        assertFalse(limiter.isStopped(6L));
        assertEquals(Duration.ofMinutes(210), store.ttls.get("stop:5"));
    }

    /** Капчу человек снимает сам, суточный лимит hh - нет, поэтому причина стопа переживает redis. */
    @Test
    void stopRemembersWhyItWasSet() {
        RateLimiter limiter = at("2026-10-03T20:30:00");

        limiter.stopUntilTomorrow(5L, RateLimiter.Stop.CAPTCHA);
        assertEquals(Optional.of(RateLimiter.Stop.CAPTCHA), limiter.stop(5L));

        limiter.stopUntilTomorrow(5L, RateLimiter.Stop.HH_LIMIT);
        assertEquals(Optional.of(RateLimiter.Stop.HH_LIMIT), limiter.stop(5L));

        limiter.clearStop(5L);
        assertEquals(Optional.empty(), limiter.stop(5L));
    }

    /** Стоп, записанный прошлой версией бота: причину не разобрать, считаем строгий случай. */
    @Test
    void unknownStopReasonCountsAsHhLimit() {
        RateLimiter limiter = at("2026-10-03T20:30:00");
        store.values.put("stop:5", "капча");

        assertEquals(Optional.of(RateLimiter.Stop.HH_LIMIT), limiter.stop(5L));
    }

    @Test
    void recheckIsAllowedOnceAMinute() {
        RateLimiter limiter = at("2026-10-03T20:30:00");

        assertTrue(limiter.allowRecheck(5L));
        assertFalse(limiter.allowRecheck(5L));
        assertTrue(limiter.allowRecheck(6L));
        assertEquals(Duration.ofMinutes(1), store.ttls.get("recheck:5"));
    }
}
