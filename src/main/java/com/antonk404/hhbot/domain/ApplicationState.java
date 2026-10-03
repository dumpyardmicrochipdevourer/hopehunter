package com.antonk404.hhbot.domain;

public enum ApplicationState {
    SENT,
    /** Пользователь нажал «Пропустить» на карточке. */
    SKIPPED,
    /** Автоматически не откликнуться (тест работодателя) - вакансия ушла пользователю ссылкой. */
    MANUAL,
    FAILED
}
