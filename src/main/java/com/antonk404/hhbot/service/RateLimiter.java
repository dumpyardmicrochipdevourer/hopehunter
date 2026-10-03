package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.store.KeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Всё, что не даёт боту вести себя как бот: дневной потолок откликов, рабочие часы и стоп до
 * завтра после капчи.
 *
 * <p>Счётчики живут в redis с ключом по дате, а не в базе: они нужны только сегодня и должны
 * исчезать сами.
 */
@Component
public class RateLimiter {

    private final KeyValueStore store;
    private final Clock clock;
    private final ZoneId zone;
    private final int workFromHour;
    private final int workToHour;

    @Autowired
    public RateLimiter(
            KeyValueStore store,
            @Value("${bot.timezone}") String timezone,
            @Value("${scanner.work-from-hour}") int workFromHour,
            @Value("${scanner.work-to-hour}") int workToHour) {
        this(store, Clock.systemUTC(), ZoneId.of(timezone), workFromHour, workToHour);
    }

    RateLimiter(KeyValueStore store, Clock clock, ZoneId zone, int workFromHour, int workToHour) {
        this.store = store;
        this.clock = clock;
        this.zone = zone;
        this.workFromHour = workFromHour;
        this.workToHour = workToHour;
    }

    public int sentToday(ResponseRule rule) {
        return store.get(counterKey(rule)).map(Integer::parseInt).orElse(0);
    }

    public boolean exhausted(ResponseRule rule) {
        return sentToday(rule) >= rule.getDailyLimit();
    }

    public void recordSent(ResponseRule rule) {
        // Сутки с запасом: ключ содержит дату, после полуночи его всё равно никто не прочтёт.
        store.increment(counterKey(rule), Duration.ofHours(48));
    }

    /** Отклики в три часа ночи - самый дешёвый для hh признак автоматики. */
    public boolean withinWorkingHours() {
        int hour = now().getHour();
        return hour >= workFromHour && hour < workToHour;
    }

    /** Останавливает все отклики пользователя до полуночи: после капчи или суточного лимита hh. */
    public void stopUntilTomorrow(Long userId, String reason) {
        ZonedDateTime now = now();
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone);
        store.set(stopKey(userId), reason, Duration.between(now, midnight));
    }

    public boolean isStopped(Long userId) {
        return store.get(stopKey(userId)).isPresent();
    }

    private ZonedDateTime now() {
        return clock.instant().atZone(zone);
    }

    private String counterKey(ResponseRule rule) {
        return "lim:" + rule.getId() + ":" + now().toLocalDate();
    }

    private static String stopKey(Long userId) {
        return "stop:" + userId;
    }
}
