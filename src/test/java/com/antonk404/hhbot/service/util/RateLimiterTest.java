package com.antonk404.hhbot.service.util;

import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.store.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RateLimiterTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private final ApplicationLogRepository logs = mock(ApplicationLogRepository.class);
    private RateLimiter at(String time) {
        return new RateLimiter(store, logs, Clock.fixed(LocalDateTime.parse(time).atZone(MOSCOW).toInstant(), MOSCOW), MOSCOW);
    }
    @Test void oneHundredSuccessfulResponsesForTheWholeUser() {
        var limiter = at("2026-10-03T02:00:00");
        when(logs.countByUserIdAndStateAndCreatedAtGreaterThanEqual(eq(1L), eq(ApplicationState.SENT), any()))
                .thenReturn(99L, 100L);
        assertFalse(limiter.exhausted(1L));
        assertTrue(limiter.exhausted(1L));
        assertFalse(limiter.exhausted(2L));
        verify(logs, times(2)).countByUserIdAndStateAndCreatedAtGreaterThanEqual(
                1L, ApplicationState.SENT, Instant.parse("2026-10-02T21:00:00Z"));
    }
    @Test void midnightChangesTheBudgetQueryAndRestartKeepsTheSameCount() {
        when(logs.countByUserIdAndStateAndCreatedAtGreaterThanEqual(1L, ApplicationState.SENT,
                Instant.parse("2026-10-02T21:00:00Z"))).thenReturn(100L);
        assertTrue(at("2026-10-03T23:59:59").exhausted(1L));
        assertTrue(at("2026-10-03T23:59:59").exhausted(1L));
        assertFalse(at("2026-10-04T00:00:00").exhausted(1L));
    }
    @Test void intervalIsPerUserBetweenSixtyAndOneHundredTwentySecondsAndSurvivesRestart() {
        var limiter = at("2026-10-03T12:00:00");
        for (int i = 0; i < 100; i++) {
            limiter.recordAttempt(1L);
            long seconds = store.ttls.get("cooldown:1").toSeconds();
            assertTrue(seconds >= 60 && seconds <= 120);
        }
        assertTrue(at("2026-10-03T12:00:00").coolingDown(1L));
        assertFalse(limiter.coolingDown(2L));
    }
    @Test void stopLastsUntilLocalMidnightAndKeepsReason() {
        var limiter = at("2026-10-03T20:30:00");
        limiter.stopUntilTomorrow(5L, RateLimiter.Stop.CAPTCHA);
        assertEquals(Duration.ofMinutes(210), store.ttls.get("stop:5"));
        assertEquals(Optional.of(RateLimiter.Stop.CAPTCHA), limiter.stop(5L));
        assertFalse(limiter.isStopped(6L));
        limiter.clearStop(5L);
        assertFalse(limiter.isStopped(5L));
        store.values.put("stop:5", "legacy");
        assertEquals(Optional.of(RateLimiter.Stop.HH_LIMIT), limiter.stop(5L));
    }
    @Test void recheckIsOnceAMinute() {
        var limiter = at("2026-10-03T20:30:00");
        assertTrue(limiter.allowRecheck(5L));
        assertFalse(limiter.allowRecheck(5L));
        assertTrue(limiter.allowRecheck(6L));
        assertEquals(Duration.ofMinutes(1), store.ttls.get("recheck:5"));
    }
}
