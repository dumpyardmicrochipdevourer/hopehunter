package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhVacancy;

/**
 * Куда сканер сообщает о находках. Интерфейс здесь, реализация в telegram: сервисам незачем
 * знать, как выглядит карточка в чате, а тестам сканера незачем поднимать Telegram.
 */
public interface ScanListener {

    /** Режим CONFIRM: вакансия ждёт решения человека. */
    void found(BotUser user, ResponseRule rule, HhVacancy vacancy);

    void applied(BotUser user, ResponseRule rule, HhVacancy vacancy);

    /** Автоматически не откликнуться - человеку нужна ссылка. */
    void manual(BotUser user, ResponseRule rule, HhVacancy vacancy, String reason);

    /** Отклики пользователя остановлены до завтра. */
    void stopped(BotUser user, String reason);

    void sessionExpired(BotUser user);
}
