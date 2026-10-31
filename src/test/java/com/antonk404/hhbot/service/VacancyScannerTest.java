package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.*;
import com.antonk404.hhbot.domain.dto.*;
import com.antonk404.hhbot.domain.repo.*;
import com.antonk404.hhbot.hh.*;
import com.antonk404.hhbot.hh.exceptions.*;
import com.antonk404.hhbot.service.util.RateLimiter;
import com.antonk404.hhbot.store.InMemoryKeyValueStore;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VacancyScannerTest {
    private static final HhSessionCookies COOKIES = HhSessionCookies.parse("hhtoken=t; _xsrf=x");
    private final ResponseRuleRepository rules = mock(ResponseRuleRepository.class);
    private final ApplicationLogRepository logs = mock(ApplicationLogRepository.class);
    private final UserService users = mock(UserService.class);
    private final HhAccountService account = mock(HhAccountService.class);
    private final HhGateway hh = mock(HhGateway.class);
    private final ApplyService applies = mock(ApplyService.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final ScanListener listener = mock(ScanListener.class);
    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private VacancyScanner scanner;
    private BotUser user;
    private ResponseRule rule;
    private VacancyScanner scanner() {
        return new VacancyScanner(rules, logs, users, account, hh, applies, limiter, store, listener, new VacancyQueue(store));
    }
    @BeforeEach void setup() {
        scanner = scanner();
        user = user(1L); rule = rule(7L, 1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(rules.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(rule));
        when(rules.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(rule));
        when(account.cookies(anyLong())).thenReturn(Optional.of(COOKIES));
        when(applies.applyAutomatically(any(), any(), any(), anyString())).thenReturn(new ApplyResult.Sent());
    }
    @AfterEach void shutdown() { scanner.shutdown(); }
    private static BotUser user(long id) {
        var user = new BotUser("u" + id, "test");
        ReflectionTestUtils.setField(user, "id", id); user.setChatId(id); return user;
    }
    private static ResponseRule rule(long id, long owner) {
        var rule = new ResponseRule(owner, "java");
        ReflectionTestUtils.setField(rule, "id", id);
        rule.setKeywords("java"); rule.setResume("hash", "Java"); rule.setEnabled(true); rule.setMode(RuleMode.AUTO);
        return rule;
    }
    private static HhVacancy vacancy(String id) {
        return new HhVacancy(id, "Java", "Acme", "", "", "url", false, false);
    }
    @Test void consumesOneVacancyPerTickAndPersistsTheRestAcrossRestart() {
        var first = vacancy("1"); var second = vacancy("2");
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(List.of(first, second));
        scanner.tick(1L);
        verify(applies).applyAutomatically(eq(user), eq(rule), eq(first), anyString());
        scanner.shutdown(); scanner = scanner();
        scanner.tick(1L);
        verify(applies).applyAutomatically(eq(user), eq(rule), eq(second), anyString());
        verify(hh, times(1)).search(eq(COOKIES), any(), anyInt());
    }
    @Test void cooldownAndBudgetStopBeforeSearch() {
        when(limiter.coolingDown(1L)).thenReturn(true);
        scanner.tick(1L);
        when(limiter.coolingDown(1L)).thenReturn(false);
        when(limiter.exhausted(1L)).thenReturn(true);
        scanner.tick(1L);
        verifyNoInteractions(hh, applies);
    }
    @Test void emptySearchSleepsForFifteenMinutesThenStartsFromFirstPage() {
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(List.of());
        scanner.tick(1L); scanner.tick(1L);
        verify(hh).search(eq(COOKIES), any(), eq(0));
        assertTrue(store.values.get("queue:7").contains("\"nextPage\":0"));
    }
    @Test void paginatesBeyondThreePagesUntilSearchIsEmpty() {
        when(hh.search(eq(COOKIES), any(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(2) < 5 ? List.of(vacancy("v" + call.getArgument(2))) : List.of());
        for (int i = 0; i < 7; i++) scanner.tick(1L);
        verify(applies, times(5)).applyAutomatically(eq(user), eq(rule), any(), anyString());
        verify(hh).search(eq(COOKIES), any(), eq(5));
    }
    @Test void captchaDoesNotConsumeVacancy() {
        var vacancy = vacancy("1");
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(List.of(vacancy));
        when(applies.applyAutomatically(eq(user), eq(rule), eq(vacancy), anyString())).thenReturn(new ApplyResult.Captcha(), new ApplyResult.Sent());
        scanner.tick(1L); scanner.tick(1L);
        verify(applies, times(2)).applyAutomatically(eq(user), eq(rule), eq(vacancy), anyString());
        verify(listener).stopped(user, RateLimiter.Stop.CAPTCHA);
    }
    @Test void searchCaptchaStopsOnlyItsUser() {
        when(hh.search(eq(COOKIES), any(), anyInt())).thenThrow(new HhCaptchaException());
        scanner.tick(1L);
        verify(limiter).stopUntilTomorrow(1L, RateLimiter.Stop.CAPTCHA);
        verify(listener).stopped(user, RateLimiter.Stop.CAPTCHA);
        verifyNoInteractions(applies);
    }
    @Test void rotatesRulesAndReloadsChangesBeforeSending() {
        var other = rule(8L, 1L);
        when(rules.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(other));
        when(rules.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(rule, other));
        when(hh.search(eq(COOKIES), any(), anyInt())).thenReturn(List.of(vacancy("1")));
        scanner.tick(1L); scanner.tick(1L);
        verify(applies).applyAutomatically(eq(user), eq(rule), any(), anyString());
        verify(applies).applyAutomatically(eq(user), eq(other), any(), anyString());
        other.setEnabled(false); rule.setEnabled(false);
        scanner.tick(1L);
        verify(applies, times(2)).applyAutomatically(any(), any(), any(), anyString());
    }
    @Test void confirmSendsAtMostTenCardsBeforeWaiting() {
        rule.setMode(RuleMode.CONFIRM);
        var vacancies = java.util.stream.IntStream.range(0, 20).mapToObj(i -> vacancy("v" + i)).toList();
        when(hh.search(eq(COOKIES), any(), anyInt())).thenReturn(vacancies);
        scanner.tick(1L); scanner.tick(1L);
        verify(listener, times(10)).found(eq(user), eq(rule), any());
        verifyNoInteractions(applies);
    }
    @Test void changingFiltersInvalidatesThePendingQueue() {
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(List.of(vacancy("1"), vacancy("2")));
        scanner.tick(1L);
        rule.setKeywords("kotlin");
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(List.of(vacancy("3")));
        scanner.tick(1L);
        verify(applies, never()).applyAutomatically(eq(user), eq(rule), eq(vacancy("2")), anyString());
        verify(applies).applyAutomatically(eq(user), eq(rule), eq(vacancy("3")), anyString());
    }
    @Test void networkFailureKeepsWorkAndBacksOff() {
        var vacancy = vacancy("1");
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(List.of(vacancy));
        when(applies.applyAutomatically(eq(user), eq(rule), eq(vacancy), anyString())).thenReturn(new ApplyResult.Failed("network"));
        scanner.tick(1L); scanner.tick(1L);
        verify(applies).applyAutomatically(eq(user), eq(rule), eq(vacancy), anyString());
        assertTrue(store.values.get("queue:7").contains("\"id\":\"1\""));
    }
    @Test void stoppedPausedAndExpiredUsersDoNotSend() {
        user.setPaused(true); scanner.tick(1L);
        user.setPaused(false); when(limiter.isStopped(1L)).thenReturn(true); scanner.tick(1L);
        verifyNoInteractions(hh, applies);
        when(limiter.isStopped(1L)).thenReturn(false);
        when(hh.search(eq(COOKIES), any(), anyInt())).thenThrow(new HhSessionExpiredException());
        when(account.markExpired(1L)).thenReturn(true);
        scanner.tick(1L);
        verify(listener).sessionExpired(user);
        verifyNoInteractions(applies);
    }

    @Test void shortRemainderRenewsConfirmationCooldown() throws Exception {
        rule.setMode(RuleMode.CONFIRM);
        var vacancies = java.util.stream.IntStream.range(0, 12).mapToObj(i -> vacancy("v" + i)).toList();
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(vacancies);
        when(hh.search(eq(COOKIES), any(), eq(1))).thenReturn(List.of(vacancy("next")));
        scanner.tick(1L);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var expired = json.readValue(store.values.get("queue:7"), VacancyQueue.Page.class);
        new VacancyQueue(store).save(7L, new VacancyQueue.Page(expired.fingerprint(), expired.nextPage(), expired.pending(), 1));
        scanner.tick(1L); scanner.tick(1L);
        verify(listener, times(12)).found(eq(user), eq(rule), any());
        verify(hh, never()).search(eq(COOKIES), any(), eq(1));
    }

    @Test void pauseDuringCardsStillPersistsTheThrottle() {
        rule.setMode(RuleMode.CONFIRM);
        var vacancies = java.util.stream.IntStream.range(0, 20).mapToObj(i -> vacancy("v" + i)).toList();
        when(hh.search(eq(COOKIES), any(), eq(0))).thenReturn(vacancies);
        doAnswer(call -> { user.setPaused(true); return null; }).when(listener).found(eq(user), eq(rule), any());
        scanner.tick(1L);
        user.setPaused(false);
        doNothing().when(listener).found(eq(user), eq(rule), any());
        scanner.tick(1L);
        verify(listener).found(eq(user), eq(rule), any());
    }

    @Test void modeChangedWhileSearchingCannotTriggerAnAutomaticSend() {
        var current = rule(7L, 1L); current.setMode(RuleMode.CONFIRM);
        when(hh.search(eq(COOKIES), any(), eq(0))).thenAnswer(call -> {
            when(rules.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(current));
            return List.of(vacancy("1"));
        });
        scanner.tick(1L);
        verifyNoInteractions(applies, listener);
    }

    @Test void thirtyUsersCanSearchWhileAnotherIsBlockedAndDuplicateTickIsIgnored() throws Exception {
        var allRules = new ArrayList<ResponseRule>();
        for (long id = 1; id <= 30; id++) {
            var owner = user(id); var ownedRule = rule(id, id); allRules.add(ownedRule);
            when(users.findById(id)).thenReturn(Optional.of(owner));
            when(rules.findByUserIdOrderByIdAsc(id)).thenReturn(List.of(ownedRule));
        }
        when(rules.findByEnabledTrueOrderByIdAsc()).thenReturn(allRules);
        var entered = new CountDownLatch(30); var release = new CountDownLatch(1);
        when(hh.search(eq(COOKIES), any(), anyInt())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return List.of();
        });
        try {
            scanner.scan();
            assertTrue(entered.await(5, TimeUnit.SECONDS), "all users must enter search concurrently");
            scanner.scan();
            verify(hh, times(30)).search(eq(COOKIES), any(), anyInt());
        } finally { release.countDown(); }
    }
}
