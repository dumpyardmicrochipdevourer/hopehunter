package com.antonk404.hhbot.service.util;

import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.store.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** One daily budget and one request cooldown per user, shared by every sending path. */
@Component
public class RateLimiter {
    public static final int DAILY_LIMIT = 100;
    public enum Stop { CAPTCHA, HH_LIMIT }
    private final KeyValueStore store;
    private final ApplicationLogRepository logs;
    private final Clock clock;
    private final ZoneId zone;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimiter(KeyValueStore store, ApplicationLogRepository logs,
                       @Value("${bot.timezone}") String timezone) {
        this(store, logs, Clock.systemUTC(), ZoneId.of(timezone));
    }

    RateLimiter(KeyValueStore store, ApplicationLogRepository logs, Clock clock, ZoneId zone) {
        this.store = store;
        this.logs = logs;
        this.clock = clock;
        this.zone = zone;
    }

    public int sentToday(ResponseRule rule) {
        return Math.toIntExact(logs.countByRuleIdAndStateAndCreatedAtGreaterThanEqual(
                rule.getId(), ApplicationState.SENT, midnight()));
    }

    public long sentToday(Long userId) {
        return logs.countByUserIdAndStateAndCreatedAtGreaterThanEqual(userId, ApplicationState.SENT, midnight());
    }

    public boolean exhausted(Long userId) { return sentToday(userId) >= DAILY_LIMIT; }
    public boolean coolingDown(Long userId) { return store.get("cooldown:" + userId).isPresent(); }

    /** Reserve before HTTP, so failures and process restarts also respect the interval. */
    public void recordAttempt(Long userId) {
        store.set("cooldown:" + userId, "1", Duration.ofSeconds(ThreadLocalRandom.current().nextLong(60, 121)));
    }

    public void stopUntilTomorrow(Long userId, Stop reason) {
        var now = now();
        store.set(stopKey(userId), reason.name(), Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)));
    }

    public Optional<Stop> stop(Long userId) {
        return store.get(stopKey(userId)).map(value -> value.equals(Stop.CAPTCHA.name()) ? Stop.CAPTCHA : Stop.HH_LIMIT);
    }
    public boolean isStopped(Long userId) { return stop(userId).isPresent(); }
    public void clearStop(Long userId) { store.delete(stopKey(userId)); }
    public boolean allowRecheck(Long userId) {
        return store.increment("recheck:" + userId, Duration.ofMinutes(1)) == 1L;
    }
    private ZonedDateTime now() { return clock.instant().atZone(zone); }
    private Instant midnight() { return now().toLocalDate().atStartOfDay(zone).toInstant(); }
    private static String stopKey(Long userId) { return "stop:" + userId; }
}
