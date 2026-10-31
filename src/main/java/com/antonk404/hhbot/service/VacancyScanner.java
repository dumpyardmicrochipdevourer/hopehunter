package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.*;
import com.antonk404.hhbot.domain.dto.*;
import com.antonk404.hhbot.domain.repo.*;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.exceptions.*;
import com.antonk404.hhbot.service.util.RateLimiter;
import com.antonk404.hhbot.service.util.RuleMatcher;
import com.antonk404.hhbot.store.KeyValueStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Each tick advances one user's queue without sleeping or blocking other users. */
@Service
public class VacancyScanner {
    private static final Logger log = LoggerFactory.getLogger(VacancyScanner.class);
    private static final Duration SEEN_TTL = Duration.ofDays(30);
    private static final long EMPTY_RETRY_MS = Duration.ofMinutes(15).toMillis();
    private final ResponseRuleRepository rules;
    private final ApplicationLogRepository logs;
    private final UserService users;
    private final HhAccountService accounts;
    private final HhGateway hh;
    private final ApplyService applies;
    private final RateLimiter limiter;
    private final KeyValueStore store;
    private final ScanListener listener;
    private final VacancyQueue queue;
    private final java.util.concurrent.atomic.AtomicInteger dispatchTurn = new java.util.concurrent.atomic.AtomicInteger();
    private final Set<Long> active = ConcurrentHashMap.newKeySet();
    private final Semaphore slots = new Semaphore(30);
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public VacancyScanner(ResponseRuleRepository rules, ApplicationLogRepository logs, UserService users,
                          HhAccountService accounts, HhGateway hh, ApplyService applies, RateLimiter limiter,
                          KeyValueStore store, ScanListener listener, VacancyQueue queue) {
        this.rules = rules; this.logs = logs; this.users = users; this.accounts = accounts; this.hh = hh;
        this.applies = applies; this.limiter = limiter; this.store = store; this.listener = listener; this.queue = queue;
    }

    @PreDestroy
    void shutdown() { workers.shutdownNow(); }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public void scan() {
        var ids = rules.findByEnabledTrueOrderByIdAsc().stream().map(ResponseRule::getUserId).distinct().toList();
        if (ids.isEmpty()) return;
        int start = Math.floorMod(dispatchTurn.getAndIncrement(), ids.size());
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get((start + i) % ids.size());
            if (!active.add(id)) continue;
            if (!slots.tryAcquire()) { active.remove(id); continue; }
            try {
                workers.submit(() -> {
                    long tickStarted = System.nanoTime();
                    try { tick(id); }
                    catch (RuntimeException e) { log.error("queue tick failed for user {}", id, e); }
                    finally {
                        active.remove(id); slots.release();
                        log.debug("queue user {} completed in {} ms; active {}", id,
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tickStarted), active.size());
                    }
                });
            } catch (RuntimeException e) { active.remove(id); slots.release(); throw e; }
        }
    }

    void tick(Long userId) {
        var user = users.findById(userId).orElse(null);
        if (user == null || user.isPaused() || user.getChatId() == null || limiter.isStopped(userId)
                || limiter.coolingDown(userId) || limiter.exhausted(userId) || Thread.currentThread().isInterrupted()) return;
        var enabled = rules.findByUserIdOrderByIdAsc(userId).stream().filter(ResponseRule::isEnabled)
                .filter(ResponseRule::isReady).toList();
        if (enabled.isEmpty()) return;
        // Rotate fairly so one rule with an endless stream cannot starve the others.
        long turn = store.increment("queue-turn:" + userId, Duration.ofDays(30)) - 1;
        var rule = enabled.get((int) Math.floorMod(turn, enabled.size()));
        scanRule(user, rule);
    }

    private void scanRule(BotUser user, ResponseRule rule) {
        String fingerprint = fingerprint(rule);
        var page = queue.load(rule.getId(), fingerprint);
        long now = System.currentTimeMillis();
        if (page.retryAt() > now) return;
        var cookies = accounts.cookies(user.getId());
        if (cookies.isEmpty()) return;
        try {
            if (page.pending().isEmpty()) {
                var found = hh.search(cookies.get(), query(rule), page.nextPage());
                page = found.isEmpty()
                        ? new VacancyQueue.Page(fingerprint, 0, List.of(), now + EMPTY_RETRY_MS)
                        : new VacancyQueue.Page(fingerprint, page.nextPage() + 1, found, 0);
                queue.save(rule.getId(), page);
                if (found.isEmpty()) return;
            }
            var pending = page.pending();
            int cards = 0;
            while (!pending.isEmpty()) {
                var currentUser = users.findById(user.getId()).orElse(null);
                var currentRule = rules.findByIdAndUserId(rule.getId(), user.getId()).orElse(null);
                if (currentUser == null || currentUser.isPaused() || limiter.isStopped(user.getId())
                        || currentRule == null || !currentRule.isEnabled() || !currentRule.isReady()
                        || !fingerprint.equals(fingerprint(currentRule))) {
                    checkpoint(rule, page, pending, cards);
                    return;
                }
                user = currentUser;
                HhVacancy vacancy = pending.getFirst();
                String seenKey = "seen:" + user.getId();
                if (store.isMember(seenKey, vacancy.id()) || logs.existsByUserIdAndVacancyId(user.getId(), vacancy.id())
                        || RuleMatcher.rejection(rule, vacancy).isPresent() || (vacancy.hasTest() && rule.isSkipWithTest())) {
                    pending.removeFirst();
                    continue;
                }
                if (rule.getMode() == RuleMode.CONFIRM || vacancy.hasTest()) {
                    if (vacancy.hasTest()) listener.manual(user, rule, vacancy, "нужно пройти тест");
                    else listener.found(user, rule, vacancy);
                    store.addToSet(seenKey, vacancy.id(), SEEN_TTL);
                    pending.removeFirst();
                    if (++cards >= 10) {
                        page = new VacancyQueue.Page(fingerprint, page.nextPage(), pending, now + EMPTY_RETRY_MS);
                        break;
                    }
                    continue;
                }
                if (vacancy.letterRequired() && applies.letter(user, rule, vacancy).isEmpty()) {
                    listener.manual(user, rule, vacancy, "работодатель требует письмо");
                    store.addToSet(seenKey, vacancy.id(), SEEN_TTL);
                    pending.removeFirst();
                    if (++cards >= 10) break;
                    continue;
                }
                var result = applies.applyAutomatically(user, rule, vacancy, fingerprint);
                switch (result) {
                    case ApplyResult.Sent sent -> { pending.removeFirst(); listener.applied(user, rule, vacancy); }
                    case ApplyResult.AlreadyApplied already -> pending.removeFirst();
                    case ApplyResult.NeedsTest test -> { pending.removeFirst(); listener.manual(user, rule, vacancy, "нужно пройти тест"); }
                    case ApplyResult.Captcha captcha -> listener.stopped(user, RateLimiter.Stop.CAPTCHA);
                    case ApplyResult.LimitReached limit -> listener.stopped(user, RateLimiter.Stop.HH_LIMIT);
                    case ApplyResult.SessionExpired expired -> listener.sessionExpired(user);
                    case ApplyResult.Deferred deferred -> { }
                    case ApplyResult.Failed failed -> {
                        // Retry later without letting a failing vacancy starve the rest of the page.
                        pending.removeFirst(); pending.addLast(vacancy);
                        page = new VacancyQueue.Page(fingerprint, page.nextPage(), pending, now + 120_000);
                    }
                }
                break;
            }
            checkpoint(rule, page, pending, cards);
        } catch (HhCaptchaException e) {
            limiter.stopUntilTomorrow(user.getId(), RateLimiter.Stop.CAPTCHA);
            listener.stopped(user, RateLimiter.Stop.CAPTCHA);
        } catch (HhSessionExpiredException e) {
            if (accounts.markExpired(user.getId())) listener.sessionExpired(user);
        } catch (HhException e) {
            queue.save(rule.getId(), new VacancyQueue.Page(fingerprint, page.nextPage(), page.pending(), now + 120_000));
            log.warn("hh search failed for rule {}", rule.getId());
        }
    }

    private void checkpoint(ResponseRule rule, VacancyQueue.Page page, List<HhVacancy> pending, int cards) {
        long retryAt = cards > 0 ? System.currentTimeMillis() + EMPTY_RETRY_MS : page.retryAt();
        queue.save(rule.getId(), new VacancyQueue.Page(page.fingerprint(), page.nextPage(), pending, retryAt));
    }

    public List<HhVacancy> preview(BotUser user, ResponseRule rule) {
        var cookies = accounts.cookies(user.getId()).orElseThrow(HhSessionExpiredException::new);
        return hh.search(cookies, query(rule), 0).stream()
                .filter(vacancy -> RuleMatcher.rejection(rule, vacancy).isEmpty()).toList();
    }

    static String fingerprint(ResponseRule rule) {
        return query(rule).toString() + "|" + rule.getMinusWords() + "|" + rule.getCompanyBlacklist()
                + "|" + rule.isSkipWithTest() + "|" + rule.getMode() + "|" + rule.getResumeHash()
                + "|" + rule.getLetterTemplateId();
    }

    static SearchQuery query(ResponseRule rule) {
        return new SearchQuery(rule.getKeywords(), rule.isTitleOnly(), rule.getSkills(), rule.getAreaId(),
                rule.getSalaryFrom(), rule.isOnlyWithSalary(), rule.getExperiences(), rule.getWorkFormats(),
                rule.getEmploymentForms(), rule.getLabels(), rule.getPeriodDays());
    }
}
