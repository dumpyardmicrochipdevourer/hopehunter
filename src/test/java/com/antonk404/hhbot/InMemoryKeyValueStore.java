package com.antonk404.hhbot;

import com.antonk404.hhbot.store.KeyValueStore;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Redis для тестов: те же операции, без ttl - тесты его не ждут, а только записывают. */
public class InMemoryKeyValueStore implements KeyValueStore {

    public final Map<String, String> values = new HashMap<>();
    public final Map<String, Set<String>> sets = new HashMap<>();
    public final Map<String, Duration> ttls = new HashMap<>();

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        values.put(key, value);
        ttls.put(key, ttl);
    }

    @Override
    public void delete(String key) {
        values.remove(key);
        sets.remove(key);
    }

    @Override
    public long increment(String key, Duration ttl) {
        long next = Long.parseLong(values.getOrDefault(key, "0")) + 1;
        values.put(key, String.valueOf(next));
        return next;
    }

    @Override
    public boolean addToSet(String key, String member, Duration ttl) {
        return sets.computeIfAbsent(key, ignored -> new HashSet<>()).add(member);
    }

    @Override
    public boolean isMember(String key, String member) {
        return sets.getOrDefault(key, Set.of()).contains(member);
    }
}
