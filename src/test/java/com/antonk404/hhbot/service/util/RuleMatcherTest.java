package com.antonk404.hhbot.service.util;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleMatcherTest {

    private static HhVacancy vacancy(String name, String company) {
        return new HhVacancy("1", name, company, "", "", "u", false, false);
    }

    @Test
    void ruleWithoutFiltersAcceptsAnything() {
        ResponseRule rule = new ResponseRule(1L, "java");

        assertEquals(Optional.empty(), RuleMatcher.rejection(rule, vacancy("Java Developer", "Acme")));
    }

    @Test
    void minusWordInTitleRejectsRegardlessOfCase() {
        ResponseRule rule = new ResponseRule(1L, "java");
        rule.setMinusWords("Senior, 1C ");

        assertEquals(Optional.of("минус-слово «senior»"),
                RuleMatcher.rejection(rule, vacancy("SENIOR Java Developer", "Acme")));
        assertTrue(RuleMatcher.rejection(rule, vacancy("Java Developer", "Acme")).isEmpty());
    }

    @Test
    void blacklistedCompanyRejectsBySubstring() {
        ResponseRule rule = new ResponseRule(1L, "java");
        rule.setCompanyBlacklist("кадровое агентство");

        assertEquals(Optional.of("компания в стоп-листе"),
                RuleMatcher.rejection(rule, vacancy("Java", "Кадровое агентство HireWay")));
    }

    @Test
    void listDropsBlanks() {
        assertEquals(List.of("a", "b"), RuleMatcher.list(" A, ,b,, "));
        assertEquals(List.of(), RuleMatcher.list(null));
    }
}
