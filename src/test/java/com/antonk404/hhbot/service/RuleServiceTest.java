package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.dto.RuleData;
import com.antonk404.hhbot.domain.dto.RuleView;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.service.exceptions.NotFoundException;
import com.antonk404.hhbot.service.exceptions.ValidationException;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleServiceTest {

    private final ResponseRuleRepository rules = mock(ResponseRuleRepository.class);
    private final LetterTemplateRepository templates = mock(LetterTemplateRepository.class);
    private final HhAccountService account = mock(HhAccountService.class);
    private final VacancyScanner scanner = mock(VacancyScanner.class);
    private final RateLimiter limiter = mock(RateLimiter.class);

    private final RuleService service = new RuleService(rules, templates, account, scanner, limiter);

    private BotUser user;
    private ResponseRule existing;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
        existing = new ResponseRule(1L, "java");
        ReflectionTestUtils.setField(existing, "id", 7L);
        existing.setKeywords("java");
        existing.setResume("hash", "Java dev");
        when(rules.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(existing));
        when(rules.save(any())).thenAnswer(call -> call.getArgument(0));
        when(account.profile(1L)).thenReturn(new HhProfile("Антон", List.of(new HhResume("hash", "Java dev"),
                new HhResume("other", "Kotlin dev"))));
    }

    private static RuleData data(String name, String keywords, String resumeHash, Integer limit) {
        return new RuleData(name, keywords, " ", null, null, null, null, null, null, null, null, resumeHash,
                null, null, limit, null, null);
    }

    /** Правило с одним необычным полем - остальное как в {@link #data}. */
    private static RuleData with(Integer salary, String experience, Long letterId, Integer interval) {
        return new RuleData("java", "java", null, null, null, null, salary, null, experience, null, null, "hash",
                letterId, null, null, interval, null);
    }

    @Test
    void createsDisabledRuleWithSafeDefaults() {
        RuleView view = service.create(user, data(" java ", "java", "hash", null));

        assertEquals("java", view.name());
        assertFalse(view.enabled());
        assertEquals(RuleMode.CONFIRM, view.mode());
        assertTrue(view.titleOnly());
        assertEquals(30, view.dailyLimit());
        assertEquals(15, view.intervalMinutes());
        assertTrue(view.onlyWithSalary());
        assertTrue(view.skipWithTest());
        assertNull(view.minusWords());
        assertEquals("Java dev", view.resumeTitle());
    }

    @Test
    void resumeMustExistOnHh() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(user, data("java", "java", "made-up", null)));

        assertEquals("resumeHash", e.getField());
    }

    /** Сохранение формы без смены резюме не должно зависеть от того, жив ли сейчас hh. */
    @Test
    void unchangedResumeDoesNotCallHh() {
        service.update(user, 7L, data("java 2", "java", "hash", 10));

        verify(account, never()).profile(any());
        assertEquals("java 2", existing.getName());
        assertEquals(10, existing.getDailyLimit());
    }

    @Test
    void rejectsOutOfRangeValuesByField() {
        assertEquals("dailyLimit", assertThrows(ValidationException.class,
                () -> service.update(user, 7L, data("java", "java", "hash", 101))).getField());
        assertEquals("name", assertThrows(ValidationException.class,
                () -> service.update(user, 7L, data(" ", "java", "hash", 10))).getField());
        assertEquals("experience", assertThrows(ValidationException.class,
                () -> service.update(user, 7L, with(null, "tenYears", null, null))).getField());
        assertEquals("salaryFrom", assertThrows(ValidationException.class,
                () -> service.update(user, 7L, with(-5, null, null, null))).getField());
    }

    @Test
    void intervalMustBeOneOfTheOffered() {
        assertEquals("intervalMinutes", assertThrows(ValidationException.class,
                () -> service.update(user, 7L, with(null, null, null, 1))).getField());

        service.update(user, 7L, with(null, null, null, 60));
        assertEquals(60, existing.getIntervalMinutes());
    }

    @Test
    void areaKeepsItsNameAndClearsWithIt() {
        service.update(user, 7L, new RuleData("java", "java", null, null, 88, " Казань ", null, null, null, null,
                null, "hash", null, null, null, null, null));
        assertEquals(88, existing.getAreaId());
        assertEquals("Казань", existing.getAreaName());

        service.update(user, 7L, new RuleData("java", "java", null, null, null, "Казань", null, null, null, null,
                null, "hash", null, null, null, null, null));
        assertNull(existing.getAreaName());
    }

    @Test
    void someoneElsesLetterCannotBeAttached() {
        when(templates.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertEquals("letterTemplateId", assertThrows(ValidationException.class,
                () -> service.update(user, 7L, with(null, null, 5L, null))).getField());

        when(templates.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(new LetterTemplate(1L, "main", "text")));
        service.update(user, 7L, with(null, null, 5L, null));
        assertEquals(5L, existing.getLetterTemplateId());
    }

    @Test
    void someoneElsesRuleLooksLikeAMissingOne() {
        when(rules.findByIdAndUserId(8L, 1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(user, 8L));
        assertThrows(NotFoundException.class, () -> service.delete(user, 8L));
    }

    @Test
    void enablingNeedsKeywordsResumeAndAccount() {
        when(account.cookies(1L)).thenReturn(Optional.empty());
        assertNull(assertThrows(ValidationException.class, () -> service.setEnabled(user, 7L, true)).getField());

        when(account.cookies(1L)).thenReturn(Optional.of(HhSessionCookies.parse("hhtoken=t")));
        assertTrue(service.setEnabled(user, 7L, true).enabled());

        existing.setResume(null, null);
        existing.setEnabled(false);
        assertEquals("resumeHash",
                assertThrows(ValidationException.class, () -> service.setEnabled(user, 7L, true)).getField());
        assertFalse(service.setEnabled(user, 7L, false).enabled());
    }

    /** Стёрли ключевые слова у работающего правила - оно не должно остаться включённым. */
    @Test
    void editThatBreaksAnEnabledRuleTurnsItOff() {
        existing.setEnabled(true);

        RuleView view = service.update(user, 7L, data("java", " ", "hash", 10));

        assertFalse(view.enabled());
        assertFalse(view.ready());
    }
}
