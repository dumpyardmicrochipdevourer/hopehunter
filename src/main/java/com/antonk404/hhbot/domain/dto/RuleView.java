package com.antonk404.hhbot.domain.dto;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;

import java.util.List;

/**
 * @param ready     заданы ключевые слова и резюме - правило можно включать
 * @param sentToday сколько откликов по нему уже ушло сегодня
 */
public record RuleView(
        Long id,
        String name,
        String keywords,
        String skills,
        String minusWords,
        boolean titleOnly,
        Integer areaId,
        String areaName,
        Integer salaryFrom,
        boolean onlyWithSalary,
        String experience,
        List<String> workFormats,
        List<String> employmentForms,
        List<String> labels,
        Integer periodDays,
        String companyBlacklist,
        String resumeHash,
        String resumeTitle,
        Long letterTemplateId,
        RuleMode mode,
        int dailyLimit,
        int intervalMinutes,
        boolean skipWithTest,
        boolean enabled,
        boolean ready,
        int sentToday) {

    public static RuleView of(ResponseRule rule, int sentToday) {
        return new RuleView(rule.getId(), rule.getName(), rule.getKeywords(), rule.getSkills(), rule.getMinusWords(),
                rule.isTitleOnly(), rule.getAreaId(), rule.getAreaName(), rule.getSalaryFrom(),
                rule.isOnlyWithSalary(), rule.getExperience(),
                rule.getWorkFormats(), rule.getEmploymentForms(), rule.getLabels(), rule.getPeriodDays(),
                rule.getCompanyBlacklist(), rule.getResumeHash(), rule.getResumeTitle(),
                rule.getLetterTemplateId(), rule.getMode(), rule.getDailyLimit(), rule.getIntervalMinutes(),
                rule.isSkipWithTest(), rule.isEnabled(),
                rule.isReady(), sentToday);
    }
}
