package com.antonk404.hhbot.domain.dto;

import com.antonk404.hhbot.domain.RuleMode;

/**
 * Правило так, как его присылает форма. Приходит целиком при создании и при сохранении: форма
 * одна, и частичные обновления только добавили бы способов рассинхронизироваться.
 *
 * @param resumeHash       null - резюме не выбрано
 * @param letterTemplateId null - без письма
 * @param areaName         название региона из подсказки; сервер его только хранит и показывает
 * @param dailyLimit       null - значение по умолчанию
 * @param intervalMinutes  null - значение по умолчанию
 */
public record RuleData(
        String name,
        String keywords,
        String minusWords,
        Boolean titleOnly,
        Integer areaId,
        String areaName,
        Integer salaryFrom,
        Boolean onlyWithSalary,
        String experience,
        Boolean remoteOnly,
        String companyBlacklist,
        String resumeHash,
        Long letterTemplateId,
        RuleMode mode,
        Integer dailyLimit,
        Integer intervalMinutes,
        Boolean skipWithTest) {
}
