package com.antonk404.hhbot.service.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Short-lived keyed locks; waiting and network IO do not pin Java 21 virtual threads. */
public final class UserLocks {
    private static final class Entry {
        final ReentrantLock lock = new ReentrantLock();
        int references;
    }
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();

    public <T> T withLock(Long userId, Supplier<T> work) {
        Entry entry = entries.compute(userId, (id, found) -> {
            Entry value = found == null ? new Entry() : found;
            value.references++;
            return value;
        });
        entry.lock.lock();
        try { return work.get(); }
        finally {
            entry.lock.unlock();
            entries.compute(userId, (id, found) -> --entry.references == 0 ? null : entry);
        }
    }
}
