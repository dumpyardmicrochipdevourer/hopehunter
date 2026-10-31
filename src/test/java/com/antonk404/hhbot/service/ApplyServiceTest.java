package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.ApplicationLog;
import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.service.util.RateLimiter;
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

    private final com.antonk404.hhbot.domain.repo.BotUserRepository users = mock(com.antonk404.hhbot.domain.repo.BotUserRepository.class);
    private final com.antonk404.hhbot.domain.repo.ResponseRuleRepository rules = mock(com.antonk404.hhbot.domain.repo.ResponseRuleRepository.class);
    private final ApplyService service = new ApplyService(gateway, account, limiter, logs, templates, users, rules);

    private BotUser user;
    private ResponseRule rule;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
        rule = new ResponseRule(1L, "java");
        ReflectionTestUtils.setField(rule, "id", 7L);
        rule.setResume("hash", "Java dev");
        rule.setKeywords("java"); rule.setEnabled(true);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(rules.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(rule));
        when(account.cookies(1L)).thenReturn(Optional.of(COOKIES));
    }

    private ApplicationLog savedLog() {
        ArgumentCaptor<ApplicationLog> captor = ArgumentCaptor.forClass(ApplicationLog.class);
        verify(logs).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void stoppedUserCannotSendByButton() {
        when(limiter.stop(1L)).thenReturn(Optional.of(RateLimiter.Stop.CAPTCHA));
        assertInstanceOf(ApplyResult.Captcha.class, service.apply(user, rule, VACANCY));
        verifyNoInteractions(gateway);
    }

    @Test
    void userDailyLimitBlocksAllRules() {
        when(limiter.exhausted(1L)).thenReturn(true);
        assertInstanceOf(ApplyResult.Deferred.class, service.apply(user, rule, VACANCY));
        verifyNoInteractions(gateway);
    }

    @Test
    void cooldownDoesNotSendAnotherRequest() {
        when(limiter.coolingDown(1L)).thenReturn(true);
        assertInstanceOf(ApplyResult.Deferred.class, service.apply(user, rule, VACANCY));
        verifyNoInteractions(gateway);
    }

    @Test
    void concurrentRulesShareTheLastDailySlotAndCooldown() throws Exception {
        var realStore = new com.antonk404.hhbot.store.InMemoryKeyValueStore();
        var realLimiter = new RateLimiter(realStore, logs, "Europe/Moscow");
        var sent = new java.util.concurrent.atomic.AtomicLong(99);
        when(logs.countByUserIdAndStateAndCreatedAtGreaterThanEqual(eq(1L), eq(ApplicationState.SENT), any()))
                .thenAnswer(call -> sent.get());
        when(logs.save(any())).thenAnswer(call -> { sent.incrementAndGet(); return call.getArgument(0); });
        var another = new ResponseRule(1L, "second");
        ReflectionTestUtils.setField(another, "id", 8L);
        another.setKeywords("java"); another.setResume("hash", "Java"); another.setEnabled(true);
        when(rules.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(another));
        when(gateway.apply(any(), any(), any(), any())).thenReturn(new ApplyResult.Sent());
        var realService = new ApplyService(gateway, account, realLimiter, logs, templates, users, rules);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<ApplyResult>>();
            for (int i = 0; i < 30; i++) {
                var selected = i % 2 == 0 ? rule : another;
                jobs.add(workers.submit(() -> realService.apply(user, selected, VACANCY)));
            }
            int successes = 0;
            for (var job : jobs) if (job.get() instanceof ApplyResult.Sent) successes++;
            assertEquals(1, successes);
        }
        assertEquals(100, sent.get());
        verify(gateway).apply(any(), any(), any(), any());
    }

    @Test
    void disabledRuleAndFreshPausePreventRequests() {
        rule.setEnabled(false);
        assertInstanceOf(ApplyResult.Deferred.class, service.apply(user, rule, VACANCY));
        rule.setEnabled(true); user.setPaused(true);
        assertInstanceOf(ApplyResult.Deferred.class, service.apply(user, rule, VACANCY));
        verifyNoInteractions(gateway);
    }

    @Test
    void automaticSendRejectsModeAndFilterEditsAfterSearch() {
        rule.setMode(com.antonk404.hhbot.domain.RuleMode.AUTO);
        String expected = VacancyScanner.fingerprint(rule);
        rule.setMode(com.antonk404.hhbot.domain.RuleMode.CONFIRM);
        assertInstanceOf(ApplyResult.Deferred.class, service.applyAutomatically(user, rule, VACANCY, expected));
        rule.setMode(com.antonk404.hhbot.domain.RuleMode.AUTO);
        rule.setKeywords("kotlin");
        assertInstanceOf(ApplyResult.Deferred.class, service.applyAutomatically(user, rule, VACANCY, expected));
        verifyNoInteractions(gateway);
    }

    @Test
    void sentIsCountedAndLogged() {
        when(gateway.apply(COOKIES, "42", "hash", "")).thenReturn(new ApplyResult.Sent());

        assertInstanceOf(ApplyResult.Sent.class, service.apply(user, rule, VACANCY));

        verify(limiter).recordAttempt(1L);
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
        verify(limiter).stopUntilTomorrow(1L, RateLimiter.Stop.CAPTCHA);

        when(gateway.apply(any(), any(), any(), any())).thenReturn(new ApplyResult.LimitReached());
        service.apply(user, rule, VACANCY);
        verify(limiter).stopUntilTomorrow(1L, RateLimiter.Stop.HH_LIMIT);


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
