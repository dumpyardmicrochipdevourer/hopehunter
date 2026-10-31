package com.antonk404.hhbot.telegram;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/** Ordered updates per user, with independent execution between users. */
@Component
public class UserUpdates implements AutoCloseable {
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    private final Semaphore capacity = new Semaphore(1024);

    public void submit(Long userId, Runnable work) {
        // Backpressure only at exceptional overload; regular traffic never waits here.
        capacity.acquireUninterruptibly();
        CompletableFuture<Void> next;
        try {
            next = tails.compute(userId, (id, previous) -> {
                var ready = previous == null ? CompletableFuture.<Void>completedFuture(null) : previous;
                return ready.handle((ignored, error) -> null).thenRunAsync(work, workers);
            });
        } catch (RuntimeException e) { capacity.release(); throw e; }
        next.whenComplete((ignored, error) -> { capacity.release(); tails.remove(userId, next); });
    }

    @Override @PreDestroy
    public void close() { workers.shutdownNow(); }
}
