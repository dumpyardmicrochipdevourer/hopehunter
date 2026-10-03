package com.antonk404.hhbot.store;

import java.time.Duration;
import java.util.Optional;

/**
 * Узкая обёртка над redis - ровно те операции, которые нужны боту.
 *
 * <p>Интерфейс, а не {@code StringRedisTemplate} напрямую, ради тестов: лимиты и дедуп - это
 * логика, которую хочется проверять без поднятого redis.
 */
public interface KeyValueStore {

    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);

    void delete(String key);

    /** Увеличивает счётчик и возвращает новое значение; ttl ставится при создании ключа. */
    long increment(String key, Duration ttl);

    /** Добавляет в множество и продлевает ему жизнь. {@code true} - элемента там ещё не было. */
    boolean addToSet(String key, String member, Duration ttl);

    boolean isMember(String key, String member);
}
