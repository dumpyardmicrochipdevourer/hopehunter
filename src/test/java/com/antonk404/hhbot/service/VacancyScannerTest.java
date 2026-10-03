package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.util.Pauser;
import com.antonk404.hhbot.service.util.RateLimiter;
import com.antonk404.hhbot.store.InMemoryKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VacancyScannerTest {

    private static final HhSessionCookies COOKIES = HhSessionCookies.parse("hhtoken=t; _xsrf=x");

    private final ResponseRuleRepository rules = mock(ResponseRuleRepository.class);
    private final ApplicationLogRepository logs = mock(ApplicationLogRepository.class);
    private final WhitelistService whitelist = mock(WhitelistService.class);
    private final HhAccountService account = mock(HhAccountService.class);
    private final HhGateway gateway = mock(HhGateway.class);
    private final ApplyService applyService = mock(ApplyService.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final ScanListener listener = mock(ScanListener.class);
    private final Pauser pauser = mock(Pauser.class);
    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();

    private final VacancyScanner scanner = new VacancyScanner(
            rules, logs, whitelist, account, gateway, applyService, limiter, store, listener, pauser, 3);

    private BotUser user;
    private ResponseRule rule;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setChatId(100L);
        rule = new ResponseRule(1L, "java");
        ReflectionTestUtils.setField(rule, "id", 7L);
        rule.setKeywords("java");
        rule.setResume("hash", "Java dev");
        rule.setEnabled(true);

        when(whitelist.findById(1L)).thenReturn(Optional.of(user));
        when(account.cookies(1L)).thenReturn(Optional.of(COOKIES));
    }

    private static HhVacancy vacancy(String id, String name) {
        return new HhVacancy(id, name, "Acme", "", "Москва", "https://hh.ru/vacancy/" + id, false, false);
    }

    private void found(HhVacancy... vacancies) {
        when(gateway.search(eq(COOKIES), any(), anyInt())).thenReturn(List.of(vacancies));
    }

    @Test
    void confirmModeShowsCardOnceAndNeverApplies() {
        found(vacancy("1", "Java"));

        scanner.scanRule(rule);
        store.delete("scan:7");
        scanner.scanRule(rule);

        verify(listener, times(1)).found(user, rule, vacancy("1", "Java"));
        verifyNoInteractions(applyService);
    }

    @Test
    void autoModeAppliesAndPausesBetweenApplies() {
        rule.setMode(RuleMode.AUTO);
        found(vacancy("1", "Java"), vacancy("2", "Java 2"));
        when(applyService.apply(any(), any(), any())).thenReturn(new ApplyResult.Sent());

        scanner.scanRule(rule);

        verify(applyService, times(2)).apply(eq(user), eq(rule), any());
        verify(listener, times(2)).applied(eq(user), eq(rule), any());
        verify(pauser, times(1)).pause();
    }

    @Test
    void minusWordVacancyIsDroppedSilently() {
        rule.setMode(RuleMode.AUTO);
        rule.setMinusWords("senior");
        found(vacancy("1", "Senior Java"));

        scanner.scanRule(rule);

        verifyNoInteractions(applyService, listener);
    }

    @Test
    void exhaustedLimitLeavesVacancyForTomorrow() {
        rule.setMode(RuleMode.AUTO);
        found(vacancy("1", "Java"));
        when(limiter.exhausted(rule)).thenReturn(true);

        scanner.scanRule(rule);

        verifyNoInteractions(applyService);

        store.delete("scan:7");
        when(limiter.exhausted(rule)).thenReturn(false);
        when(applyService.apply(any(), any(), any())).thenReturn(new ApplyResult.Sent());
        scanner.scanRule(rule);

        verify(applyService).apply(user, rule, vacancy("1", "Java"));
    }

    @Test
    void stopsAtPerScanCap() {
        rule.setMode(RuleMode.AUTO);
        found(vacancy("1", "a"), vacancy("2", "b"), vacancy("3", "c"), vacancy("4", "d"));
        when(applyService.apply(any(), any(), any())).thenReturn(new ApplyResult.Sent());

        scanner.scanRule(rule);

        verify(applyService, times(3)).apply(any(), any(), any());
    }

    @Test
    void captchaStopsTheScanImmediately() {
        rule.setMode(RuleMode.AUTO);
        found(vacancy("1", "a"), vacancy("2", "b"));
        when(applyService.apply(any(), any(), any())).thenReturn(new ApplyResult.Captcha());

        scanner.scanRule(rule);

        verify(applyService, times(1)).apply(any(), any(), any());
        verify(listener).stopped(eq(user), any());
    }

    @Test
    void vacancyWithTestGoesToTheUserWhenNotSkipped() {
        rule.setMode(RuleMode.AUTO);
        rule.setSkipWithTest(false);
        HhVacancy withTest = new HhVacancy("1", "Java", "Acme", "", "", "u", true, false);
        found(withTest);
        when(applyService.apply(user, rule, withTest)).thenReturn(new ApplyResult.NeedsTest());

        scanner.scanRule(rule);

        verify(listener).manual(eq(user), eq(rule), eq(withTest), any());
    }

    @Test
    void vacancyWithTestIsSkippedSilentlyByDefault() {
        rule.setMode(RuleMode.AUTO);
        found(new HhVacancy("1", "Java", "Acme", "", "", "u", true, false));

        scanner.scanRule(rule);

        verifyNoInteractions(applyService, listener);
    }

    /** Сканер ходит раз в 15 минут; правило с интервалом в час должно пропускать три прохода из четырёх. */
    @Test
    void ruleIsNotScannedAgainBeforeItsInterval() {
        rule.setIntervalMinutes(60);
        found(vacancy("1", "Java"));

        scanner.scanRule(rule);
        scanner.scanRule(rule);

        verify(gateway, times(1)).search(any(), any(), anyInt());
        assertEquals(java.time.Duration.ofMinutes(58), store.ttls.get("scan:7"));
    }

    @Test
    void expiredSessionIsReportedOnce() {
        when(gateway.search(any(), any(), anyInt())).thenThrow(new HhSessionExpiredException());
        when(account.markExpired(1L)).thenReturn(true, false);

        scanner.scanRule(rule);
        store.delete("scan:7");
        scanner.scanRule(rule);

        verify(listener, times(1)).sessionExpired(user);
    }

    @Test
    void skipsPausedUserStoppedUserAndUnreadyRule() {
        found(vacancy("1", "Java"));

        user.setPaused(true);
        scanner.scanRule(rule);
        user.setPaused(false);

        when(limiter.isStopped(1L)).thenReturn(true);
        scanner.scanRule(rule);
        when(limiter.isStopped(1L)).thenReturn(false);

        rule.setKeywords(" ");
        scanner.scanRule(rule);

        verify(gateway, never()).search(any(), any(), anyInt());
    }

    @Test
    void doesNothingOutsideWorkingHours() {
        when(limiter.withinWorkingHours()).thenReturn(false);

        scanner.scan();

        verifyNoInteractions(rules, gateway);
    }
}
