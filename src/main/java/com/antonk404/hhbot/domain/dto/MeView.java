package com.antonk404.hhbot.domain.dto;

/**
 * Всё для главного экрана одним запросом.
 *
 * @param dailyLimit общий лимит бота: 100 успешных откликов на пользователя в сутки
 * @param stopped отклики остановлены (капча или суточный лимит hh)
 * @param stopReason CAPTCHA - снимается пройденной капчей, HH_LIMIT - только завтра; null - не остановлены
 * @param notifyApplied слать ли сообщение после каждого автоотклика
 */
public record MeView(
        String username,
        boolean paused,
        boolean stopped,
        String stopReason,
        AccountView account,
        int rulesTotal,
        int rulesEnabled,
        long sentToday,
        int dailyLimit,
        boolean notifyApplied) {
}