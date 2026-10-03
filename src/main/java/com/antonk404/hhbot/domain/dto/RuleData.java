package com.antonk404.hhbot.domain.dto;

import com.antonk404.hhbot.domain.RuleMode;

/**
 * Правило так, как его присылает форма. Приходит целиком при создании и при сохранении: форма
 * одна, и частичные обновления только добавили бы способов рассинхронизироваться.
 *
 * @param resumeHash       null - резюме не выбрано
 * @param letterTemplateId null - без письма
 * @param dailyLimit       null - значение по умолчанию
 */
public record RuleData(
        String name,
        String keywords,
        String minusWords,
        Boolean titleOnly,
        Integer areaId,
        Integer salaryFrom,
        String experience,
        Boolean remoteOnly,
        String companyBlacklist,
        String resumeHash,
        Long letterTemplateId,
        RuleMode mode,
        Integer dailyLimit) {
}
