package com.antonk404.hhbot.domain.dto;

/**
 * Всё для главного экрана одним запросом.
 *
 * @param dailyLimit сколько откликов в день разрешают включённые правила в сумме
 * @param stopped отклики остановлены до завтра (капча или суточный лимит hh)
 */
public record MeView(
        String username,
        boolean paused,
        boolean stopped,
        AccountView account,
        int rulesTotal,
        int rulesEnabled,
        long sentToday,
        int dailyLimit) {
}
