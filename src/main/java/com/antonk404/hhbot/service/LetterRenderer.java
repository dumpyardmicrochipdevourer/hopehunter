package com.antonk404.hhbot.service;

import com.antonk404.hhbot.hh.HhVacancy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Подставляет в шаблон письма алиасы вида {@code [company_name]}. */
public final class LetterRenderer {

    public static final List<String> ALIASES =
            List.of("company_name", "vacancy_name", "salary", "city", "my_name");

    /** Только латиница и подчёркивание: «[1]» или «[см. резюме]» в тексте письма - не алиасы. */
    private static final Pattern ALIAS = Pattern.compile("\\[([a-z_]+)]");

    private LetterRenderer() {
    }

    /**
     * Алиасы из шаблона, которых бот не знает.
     *
     * <p>Проверяется при сохранении шаблона, а не при отклике: опечатка вроде
     * {@code [compani_name]} иначе уехала бы работодателю дословно, и узнать об этом было бы не от кого.
     */
    public static List<String> unknownAliases(String body) {
        List<String> unknown = new ArrayList<>();
        Matcher matcher = ALIAS.matcher(body);
        while (matcher.find()) {
            String alias = matcher.group(1);
            if (!ALIASES.contains(alias) && !unknown.contains(alias)) {
                unknown.add(alias);
            }
        }
        return unknown;
    }

    public static String render(String body, HhVacancy vacancy, String ownerName) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("company_name", vacancy.company());
        values.put("vacancy_name", vacancy.name());
        values.put("salary", vacancy.salary());
        values.put("city", vacancy.city());
        values.put("my_name", ownerName == null ? "" : ownerName);

        String rendered = ALIAS.matcher(body).replaceAll(match -> {
            String value = values.get(match.group(1));
            return Matcher.quoteReplacement(value == null ? match.group() : value);
        });
        return tidy(rendered);
    }

    /**
     * Убирает следы пустых подстановок: у вакансии без вилки «на зарплату [salary] согласен»
     * иначе превращается в «на зарплату  согласен» с двойным пробелом и пробелом перед запятой.
     * Переводы строк не трогает - абзацы в письме авторские.
     */
    private static String tidy(String text) {
        return text.replaceAll("[ \\t]{2,}", " ")
                .replaceAll("[ \\t]+([,.;:!?])", "$1")
                .replaceAll("(?m)[ \\t]+$", "")
                .strip();
    }
}
