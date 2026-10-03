package com.antonk404.hhbot.service.util;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Локальный фильтр поверх поиска hh.
 *
 * <p>Регион, зарплату и опыт отсеивает сам hh - это параметры запроса. Здесь то, чего в запросе
 * нет или чему там нельзя доверять: минус-слова и стоп-лист компаний.
 */
public final class RuleMatcher {

    private RuleMatcher() {
    }

    /** Причина, по которой вакансия правилу не подходит; пусто - подходит. */
    public static Optional<String> rejection(ResponseRule rule, HhVacancy vacancy) {
        String name = vacancy.name().toLowerCase(Locale.ROOT);
        for (String word : list(rule.getMinusWords())) {
            if (name.contains(word)) {
                return Optional.of("минус-слово «" + word + "»");
            }
        }
        String company = vacancy.company().toLowerCase(Locale.ROOT);
        for (String blocked : list(rule.getCompanyBlacklist())) {
            if (company.contains(blocked)) {
                return Optional.of("компания в стоп-листе");
            }
        }
        return Optional.empty();
    }

    /** Разбирает «a, b ,c» в список в нижнем регистре, без пустых. */
    public static List<String> list(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(item -> item.strip().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
