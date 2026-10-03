package com.antonk404.hhbot.domain.dto;

/**
 * @param areaId id региона hh, null - везде
 * @param onlyWithSalary при заданном пороге не показывать вакансии без указанной зарплаты
 * @param experience код опыта hh (noExperience, between1And3, …), null - любой
 */
public record SearchQuery(
        String text,
        boolean titleOnly,
        Integer areaId,
        Integer salaryFrom,
        boolean onlyWithSalary,
        String experience,
        boolean remoteOnly) {
}
