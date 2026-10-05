package com.antonk404.hhbot.service.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Пауза между автооткликами. Случайная, потому что отклики ровно раз в N секунд - метроном, а не
 * человек. Отдельный бин, чтобы тесты сканера не спали по минуте.
 */
@Component
public class Pauser {

    private final int minSeconds;
    private final int maxSeconds;

    public Pauser(
            @Value("${scanner.pause-min-seconds}") int minSeconds,
            @Value("${scanner.pause-max-seconds}") int maxSeconds) {
        this.minSeconds = minSeconds;
        this.maxSeconds = Math.max(minSeconds, maxSeconds);
    }

    public void pause() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(minSeconds, maxSeconds + 1L) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
