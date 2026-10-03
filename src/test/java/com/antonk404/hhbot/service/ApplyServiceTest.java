package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.ApplicationLog;
import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.hh.ApplyResult;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.hh.HhVacancy;
import com.antonk404.hhbot.repo.ApplicationLogRepository;
import com.antonk404.hhbot.repo.LetterTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplyServiceTest {

    private static final HhSessionCookies COOKIES = HhSessionCookies.parse("hhtoken=t; _xsrf=x");
    private static final HhVacancy VACANCY =
            new HhVacancy("42", "Java dev", "Acme", "", "Москва", "https://hh.ru/vacancy/42", false, false);

    private final HhGateway gateway = mock(HhGateway.class);
    private final HhAccountService account = mock(HhAccountService.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final ApplicationLogRepository logs = mock(ApplicationLogRepository.class);
    private final LetterTemplateRepository templates = mock(LetterTemplateRepository.class);

    private final ApplyService service = new ApplyService(gateway, account, limiter, logs, templates);

    private BotUser user;
    private ResponseRule rule;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
        rule = new ResponseRule(1L, "java");
        ReflectionTestUtils.setField(rule, "id", 7L);
        rule.setResume("hash", "Java dev");
        when(account.cookies(1L)).thenReturn(Optional.of(COOKIES));
    }

    private ApplicationLog savedLog() {
        ArgumentCaptor<ApplicationLog> captor = ArgumentCaptor.forClass(ApplicationLog.class);
        verify(logs).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void sentIsCountedAndLogged() {
        when(gateway.apply(COOKIES, "42", "hash", "")).thenReturn(new ApplyResult.Sent());

        assertInstanceOf(ApplyResult.Sent.class, service.apply(user, rule, VACANCY));

        verify(limiter).recordSent(rule);
        assertEquals(ApplicationState.SENT, savedLog().getState());
    }

    @Test
    void rendersLetterFromRuleTemplate() {
        rule.setLetterTemplateId(5L);
        when(templates.findByIdAndUserId(5L, 1L))
                .thenReturn(Optional.of(new LetterTemplate(1L, "main", "Хочу в [company_name]. [my_name]")));
        when(account.session(1L)).thenReturn(Optional.of(new HhSession(1L, "enc", "Антон")));
        when(gateway.apply(any(), any(), any(), any())).thenReturn(new ApplyResult.Sent());

        service.apply(user, rule, VACANCY);

        verify(gateway).apply(COOKIES, "42", "hash", "Хочу в Acme. Антон");
    }

    @Test
    void neverAppliesTwiceToTheSameVacancy() {
        when(logs.existsByUserIdAndVacancyId(1L, "42")).thenReturn(true);

        assertInstanceOf(ApplyResult.AlreadyApplied.class, service.apply(user, rule, VACANCY));

        verifyNoInteractions(gateway);
    }

    @Test
    void vacancyWithTestIsNotSentToHh() {
        HhVacancy withTest = new HhVacancy("42", "Java", "Acme", "", "", "u", true, false);

        assertInstanceOf(ApplyResult.NeedsTest.class, service.apply(user, rule, withTest));

        verifyNoInteractions(gateway);
        assertEquals(ApplicationState.MANUAL, savedLog().getState());
    }

    @Test
    void requiredLetterWithoutTemplateIsNotSentToHh() {
        HhVacancy letterRequired = new HhVacancy("42", "Java", "Acme", "", "", "u", false, true);

        assertInstanceOf(ApplyResult.Failed.class, service.apply(user, rule, letterRequired));

        verifyNoInteractions(gateway);
        verify(logs, never()).save(any());
    }

    @Test
    void captchaAndHhLimitStopTheUserUntilTomorrow() {
        when(gateway.apply(any(), any(), any(), any())).thenReturn(new ApplyResult.Captcha());
        service.apply(user, rule, VACANCY);
        verify(limiter).stopUntilTomorrow(eq(1L), eq("капча"));

        when(gateway.apply(any(), any(), any(), any())).thenReturn(new ApplyResult.LimitReached());
        service.apply(user, rule, VACANCY);
        verify(limiter).stopUntilTomorrow(eq(1L), eq("суточный лимит hh"));

        verify(limiter, never()).recordSent(any());
        verify(logs, never()).save(any());
    }

    @Test
    void expiredSessionIsMarkedAndFailureIsNotLogged() {
        when(gateway.apply(any(), any(), any(), any())).thenReturn(new ApplyResult.SessionExpired());
        service.apply(user, rule, VACANCY);
        verify(account).markExpired(1L);

        when(gateway.apply(any(), any(), any(), any())).thenReturn(new ApplyResult.Failed("hh не ответил"));
        service.apply(user, rule, VACANCY);

        verify(logs, never()).save(any());
    }

    @Test
    void withoutSessionDoesNotCallHh() {
        when(account.cookies(1L)).thenReturn(Optional.empty());

        assertInstanceOf(ApplyResult.SessionExpired.class, service.apply(user, rule, VACANCY));

        verifyNoInteractions(gateway);
    }
}
