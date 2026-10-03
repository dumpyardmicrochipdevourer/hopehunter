package com.antonk404.hhbot.domain.dto;

/**
 * @param vacancy на какой вакансии показан пример
 * @param real    вакансия настоящая, с hh; false - выдуманная, потому что настоящую достать не вышло
 */
public record LetterPreview(String text, HhVacancy vacancy, boolean real) {
}
