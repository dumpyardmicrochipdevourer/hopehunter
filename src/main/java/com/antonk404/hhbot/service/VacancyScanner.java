package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.dto.SearchQuery;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.util.Pauser;
import com.antonk404.hhbot.service.util.RateLimiter;
import com.antonk404.hhbot.service.util.RuleMatcher;
import com.antonk404.hhbot.store.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Обходит включённые правила: ищет новые вакансии и откликается или показывает их человеку. */
@Service
public class VacancyScanner {

    private static final Logger logger = LoggerFactory.getLogger(VacancyScanner.class);

    /** Дольше вакансии на hh почти не живут; после этого срока повтор уже не повтор. */
    private static final Duration SEEN_TTL = Duration.ofDays(30);

    /**
     * Потолок карточек за проход. Первый проход нового правила находит полную страницу, и
     * пятьдесят сообщений подряд - это и спам человеку, и упор в лимиты Telegram.
     */
    static final int MAX_CARDS_PER_SCAN = 10;

    private final ResponseRuleRepository responseRuleRepository;
    private final ApplicationLogRepository applicationLogRepository;
    private final WhitelistService whitelistService;
    private final HhAccountService hhAccountService;
    private final HhGateway hhGateway;
    private final ApplyService applyService;
    private final RateLimiter rateLimiter;
    private final KeyValueStore store;
    private final ScanListener listener;
    private final Pauser pauser;
    private final int maxAppliesPerScan;

    public VacancyScanner(
            ResponseRuleRepository responseRuleRepository,
            ApplicationLogRepository applicationLogRepository,
            WhitelistService whitelistService,
            HhAccountService hhAccountService,
            HhGateway hhGateway,
            ApplyService applyService,
            RateLimiter rateLimiter,
            KeyValueStore store,
            ScanListener listener,
            Pauser pauser,
            @Value("${scanner.max-applies-per-scan}") int maxAppliesPerScan) {
        this.responseRuleRepository = responseRuleRepository;
        this.applicationLogRepository = applicationLogRepository;
        this.whitelistService = whitelistService;
        this.hhAccountService = hhAccountService;
        this.hhGateway = hhGateway;
        this.applyService = applyService;
        this.rateLimiter = rateLimiter;
        this.store = store;
        this.listener = listener;
        this.pauser = pauser;
        this.maxAppliesPerScan = maxAppliesPerScan;
    }

    // fixedDelay, не fixedRate: проход с паузами между откликами может длиться минуты, и
    // следующий не должен стартовать ему в спину.
    @Scheduled(
            fixedDelayString = "#{${scanner.interval-minutes} * 60000}",
            initialDelayString = "60000")
    public void scan() {
        if (!rateLimiter.withinWorkingHours()) {
            return;
        }
        for (ResponseRule rule : responseRuleRepository.findByEnabledTrueOrderByIdAsc()) {
            try {
                scanRule(rule);
            } catch (RuntimeException e) {
                // Одно сломанное правило не должно останавливать обход остальных.
                logger.error("scan of rule {} failed", rule.getId(), e);
            }
        }
    }

    void scanRule(ResponseRule rule) {
        Optional<BotUser> owner = whitelistService.findById(rule.getUserId());
        if (owner.isEmpty() || owner.get().isPaused() || owner.get().getChatId() == null) {
            return;
        }
        BotUser user = owner.get();
        if (!rule.isReady() || rateLimiter.isStopped(user.getId())) {
            return;
        }
        Optional<HhSessionCookies> cookies = hhAccountService.cookies(user.getId());
        if (cookies.isEmpty()) {
            return;
        }

        List<HhVacancy> vacancies;
        try {
            vacancies = hhGateway.search(cookies.get(), query(rule), 0);
        } catch (HhSessionExpiredException e) {
            expire(user);
            return;
        } catch (HhException e) {
            logger.warn("search for rule {} failed: {}", rule.getId(), e.getMessage());
            return;
        }

        String seenKey = "seen:" + user.getId();
        int applied = 0;
        int cards = 0;
        for (HhVacancy vacancy : vacancies) {
            if (store.isMember(seenKey, vacancy.id())) {
                continue;
            }
            if (applicationLogRepository.existsByUserIdAndVacancyId(user.getId(), vacancy.id())
                    || RuleMatcher.rejection(rule, vacancy).isPresent()) {
                store.addToSet(seenKey, vacancy.id(), SEEN_TTL);
                continue;
            }

            if (rule.getMode() == RuleMode.CONFIRM && !vacancy.hasTest()) {
                if (cards >= MAX_CARDS_PER_SCAN) {
                    break;
                }
                listener.found(user, rule, vacancy);
                store.addToSet(seenKey, vacancy.id(), SEEN_TTL);
                cards++;
                continue;
            }

            // Не помечаем просмотренной: вакансия, на которую сегодня не хватило лимита, должна
            // дождаться следующего прохода, а не пропасть.
            if (rateLimiter.exhausted(rule) || applied >= maxAppliesPerScan) {
                break;
            }
            if (applied > 0) {
                pauser.pause();
            }
            ApplyResult result = applyService.apply(user, rule, vacancy);
            store.addToSet(seenKey, vacancy.id(), SEEN_TTL);
            switch (result) {
                case ApplyResult.Sent sent -> {
                    applied++;
                    listener.applied(user, rule, vacancy);
                }
                case ApplyResult.NeedsTest test -> listener.manual(user, rule, vacancy, "нужно пройти тест");
                case ApplyResult.Failed failed -> listener.manual(user, rule, vacancy, failed.reason());
                case ApplyResult.AlreadyApplied already -> {
                }
                case ApplyResult.LimitReached limit -> {
                    listener.stopped(user, "hh больше не принимает отклики сегодня");
                    return;
                }
                case ApplyResult.Captcha captcha -> {
                    listener.stopped(user, "hh показал капчу");
                    return;
                }
                case ApplyResult.SessionExpired expired -> {
                    listener.sessionExpired(user);
                    return;
                }
            }
        }
    }

    /**
     * Подходящие вакансии без каких-либо действий и без записи в «просмотренные» - чтобы
     * человек увидел, что правило найдёт, до того как его включить.
     */
    public List<HhVacancy> preview(BotUser user, ResponseRule rule) {
        HhSessionCookies cookies = hhAccountService.cookies(user.getId())
                .orElseThrow(HhSessionExpiredException::new);
        return hhGateway.search(cookies, query(rule), 0).stream()
                .filter(vacancy -> RuleMatcher.rejection(rule, vacancy).isEmpty())
                .toList();
    }

    private void expire(BotUser user) {
        if (hhAccountService.markExpired(user.getId())) {
            listener.sessionExpired(user);
        }
    }

    static SearchQuery query(ResponseRule rule) {
        return new SearchQuery(rule.getKeywords(), rule.isTitleOnly(), rule.getAreaId(),
                rule.getSalaryFrom(), rule.getExperience(), rule.isRemoteOnly());
    }
}
