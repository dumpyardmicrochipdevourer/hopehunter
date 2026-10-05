package com.antonk404.hhbot.domain.dto;

import java.util.List;

/**
 * @param text            ключевые слова - что искать
 * @param titleOnly       искать ключевые слова только в названии вакансии
 * @param skills          что должно быть в описании вакансии, языком запросов hh; null - не важно
 * @param areaId          id региона hh, null - везде
 * @param onlyWithSalary  при заданном пороге не показывать вакансии без указанной зарплаты
 * @param experience      код опыта hh (noExperience, between1And3, …), null - любой
 * @param workFormats     REMOTE, HYBRID, ON_SITE, FIELD_WORK; пусто - любой
 * @param employmentForms FULL, PART, PROJECT, FLY_IN_FLY_OUT; пусто - любая
 * @param labels          метки hh вроде not_from_agency
 * @param periodDays      не старше стольких дней, null - без ограничения
 */
public record SearchQuery(
        String text,
        boolean titleOnly,
        String skills,
        Integer areaId,
        Integer salaryFrom,
        boolean onlyWithSalary,
        String experience,
        List<String> workFormats,
        List<String> employmentForms,
        List<String> labels,
        Integer periodDays) {

    /** Запрос только по словам - для тестов и мест, где остальное не важно. */
    public static SearchQuery of(String text) {
        return new SearchQuery(text, true, null, null, null, true, null, List.of(), List.of(), List.of(), null);
    }
}
