package com.antonk404.hhbot.domain.dto;

import com.antonk404.hhbot.domain.RuleMode;

import java.util.List;

/**
 * Правило так, как его присылает форма. Приходит целиком при создании и при сохранении: форма
 * одна, и частичные обновления только добавили бы способов рассинхронизироваться.
 *
 * @param resumeHash       null - резюме не выбрано
 * @param letterTemplateId null - без письма
 * @param skills           что должно быть в описании вакансии; null - не важно
 * @param workFormats      null или пусто - любой формат работы
 * @param areaName         название региона из подсказки; сервер его только хранит и показывает
 * @param dailyLimit       null - значение по умолчанию
 * @param intervalMinutes  null - значение по умолчанию
 */
public record RuleData(
        String name,
        String keywords,
        String skills,
        String minusWords,
        Boolean titleOnly,
        Integer areaId,
        String areaName,
        Integer salaryFrom,
        Boolean onlyWithSalary,
        String experience,
        List<String> workFormats,
        List<String> employmentForms,
        List<String> labels,
        Integer periodDays,
        String companyBlacklist,
        String resumeHash,
        Long letterTemplateId,
        RuleMode mode,
        Integer dailyLimit,
        Integer intervalMinutes,
        Boolean skipWithTest) {
}
