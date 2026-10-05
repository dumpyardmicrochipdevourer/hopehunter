package com.antonk404.hhbot.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisKeyValueStore implements KeyValueStore {

    private final StringRedisTemplate redis;

    public RedisKeyValueStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    @Override
    public boolean addToSet(String key, String member, Duration ttl) {
        Long added = redis.opsForSet().add(key, member);
        redis.expire(key, ttl);
        return added != null && added > 0;
    }

    @Override
    public boolean isMember(String key, String member) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key, member));
    }
}
