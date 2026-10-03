package com.antonk404.hhbot.domain.dto;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;

/**
 * @param ready     заданы ключевые слова и резюме - правило можно включать
 * @param sentToday сколько откликов по нему уже ушло сегодня
 */
public record RuleView(
        Long id,
        String name,
        String keywords,
        String minusWords,
        boolean titleOnly,
        Integer areaId,
        Integer salaryFrom,
        String experience,
        boolean remoteOnly,
        String companyBlacklist,
        String resumeHash,
        String resumeTitle,
        Long letterTemplateId,
        RuleMode mode,
        int dailyLimit,
        boolean enabled,
        boolean ready,
        int sentToday) {

    public static RuleView of(ResponseRule rule, int sentToday) {
        return new RuleView(rule.getId(), rule.getName(), rule.getKeywords(), rule.getMinusWords(),
                rule.isTitleOnly(), rule.getAreaId(), rule.getSalaryFrom(), rule.getExperience(),
                rule.isRemoteOnly(), rule.getCompanyBlacklist(), rule.getResumeHash(), rule.getResumeTitle(),
                rule.getLetterTemplateId(), rule.getMode(), rule.getDailyLimit(), rule.isEnabled(),
                rule.isReady(), sentToday);
    }
}
