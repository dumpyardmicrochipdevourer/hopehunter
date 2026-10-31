package com.antonk404.hhbot.telegram;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UserUpdatesTest {
    @Test void preservesOrderWithoutBlockingOtherUsers() throws Exception {
        try (var updates = new UserUpdates()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var other = new CountDownLatch(1); var done = new CountDownLatch(1);
            var step = new AtomicInteger();
            updates.submit(1L, () -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                step.set(1);
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            updates.submit(1L, () -> { step.compareAndSet(1, 2); done.countDown(); });
            updates.submit(2L, other::countDown);
            try { assertTrue(other.await(5, TimeUnit.SECONDS)); assertEquals(0, step.get()); }
            finally { release.countDown(); }
            assertTrue(done.await(5, TimeUnit.SECONDS)); assertEquals(2, step.get());
        }
    }
}
