package com.antonk404.hhbot.domain.dto;

/**
 * Чем закончилась попытка отклика. Sealed, потому что на каждый исход у бота своя реакция:
 * одни безобидны, после других нельзя трогать аккаунт до завтра.
 */
public sealed interface ApplyResult {

    record Sent() implements ApplyResult {
    }

    record AlreadyApplied() implements ApplyResult {
    }

    /** Работодатель требует пройти тест - только руками на сайте. */
    record NeedsTest() implements ApplyResult {
    }

    /** hh больше не принимает отклики сегодня (у сайта свой суточный потолок). */
    record LimitReached() implements ApplyResult {
    }

    /** hh показал капчу: аккаунт под подозрением, продолжать - прямой путь к блокировке. */
    record Captcha() implements ApplyResult {
    }

    record SessionExpired() implements ApplyResult {
    }

    record Failed(String reason) implements ApplyResult {
    }
}
