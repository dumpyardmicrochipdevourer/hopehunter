package com.antonk404.hhbot.domain.dto;

/**
 * Вакансия в том объёме, который нужен боту.
 *
 * @param salary уже отформатированная вилка, пустая строка если работодатель её не указал
 * @param hasTest у вакансии обязательный тест - автоматически на такую не откликнуться
 */
public record HhVacancy(
        String id,
        String name,
        String company,
        String salary,
        String city,
        String url,
        boolean hasTest,
        boolean letterRequired) {
}
