package com.antonk404.hhbot.hh;

/**
 * @param areaId id региона hh, null - везде
 * @param experience код опыта hh (noExperience, between1And3, …), null - любой
 */
public record SearchQuery(
        String text,
        boolean titleOnly,
        Integer areaId,
        Integer salaryFrom,
        String experience,
        boolean remoteOnly) {
}
