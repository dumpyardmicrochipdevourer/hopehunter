package com.antonk404.hhbot.hh;

import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhArea;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.dto.SearchQuery;
import java.util.List;

/**
 * Всё, что боту нужно от hh.ru.
 *
 * <p>Интерфейс стоит здесь не для красоты. Открытого API под отклики у hh нет, реализация ходит
 * на сайт так же, как браузер, и сломается, когда сайт поменяется. Менять тогда придётся один
 * класс, а не бота.
 */
public interface HhGateway {

    /**
     * Имя владельца и его резюме. Заодно служит проверкой сессии.
     *
     * @throws HhSessionExpiredException если hh не узнал куки
     */
    HhProfile profile(HhSessionCookies cookies);

    /**
     * Одна страница поиска, свежие первыми.
     *
     * @param page с нуля
     */
    List<HhVacancy> search(HhSessionCookies cookies, SearchQuery query, int page);

    /**
     * Подсказка регионов по началу названия - та же, что в строке поиска на сайте.
     * Сессия не нужна: справочник открыт всем.
     */
    List<HhArea> areas(String text);

    /** Не бросает на ожидаемых отказах hh - они приходят как {@link ApplyResult}. */
    ApplyResult apply(HhSessionCookies cookies, String vacancyId, String resumeHash, String letter);
}
