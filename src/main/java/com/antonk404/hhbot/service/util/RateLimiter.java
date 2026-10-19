package com.antonk404.hhbot.service.util;

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
import java.util.Optional;

/**
 * Всё, что не даёт боту вести себя как бот: дневной потолок откликов, рабочие часы и стоп до
 * завтра после капчи.
 *
 * <p>Счётчики живут в redis с ключом по дате, а не в базе: они нужны только сегодня и должны
 * исчезать сами.
 */
@Component
public class RateLimiter {

    /** Почему отклики остановлены. Разница важна: одно проходит человек, другое - полночь. */
    public enum Stop {
        /** hh показал капчу. Пройти её может только человек, зато сразу. */
        CAPTCHA,
        /** Суточный потолок самого hh: до завтра сделать нечего. */
        HH_LIMIT
    }

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
    public void stopUntilTomorrow(Long userId, Stop reason) {
        ZonedDateTime now = now();
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone);
        store.set(stopKey(userId), reason.name(), Duration.between(now, midnight));
    }

    /** Непонятное значение считаем лимитом hh: это строгий случай, снять его досрочно нельзя. */
    public Optional<Stop> stop(Long userId) {
        return store.get(stopKey(userId))
                .map(value -> value.equals(Stop.CAPTCHA.name()) ? Stop.CAPTCHA : Stop.HH_LIMIT);
    }

    public boolean isStopped(Long userId) {
        return stop(userId).isPresent();
    }

    /** Снимает стоп досрочно - когда человек прошёл капчу и hh снова пускает. */
    public void clearStop(Long userId) {
        store.delete(stopKey(userId));
    }

    /**
     * Не чаще раза в минуту. Кнопку «прошёл капчу» можно нажимать сколько угодно, а вот запрос к
     * hh на каждое нажатие - это ровно то поведение, из-за которого капча и появилась.
     */
    public boolean allowRecheck(Long userId) {
        return store.increment("recheck:" + userId, Duration.ofMinutes(1)) == 1L;
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
