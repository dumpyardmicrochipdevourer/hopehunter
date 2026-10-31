package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.ApplicationLog;
import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.domain.repo.BotUserRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.service.util.LetterRenderer;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Один отклик от начала до конца: письмо, запрос в hh, журнал и последствия для аккаунта.
 *
 * <p>Общий для сканера и для кнопки на карточке, чтобы оба пути одинаково считали лимит и
 * одинаково останавливались на капче.
 */
@Service
public class ApplyService {

    private final BotUserRepository users;
    private final ResponseRuleRepository rules;
    private final HhGateway hhGateway;
    private final HhAccountService hhAccountService;
    private final RateLimiter rateLimiter;
    private final ApplicationLogRepository applicationLogRepository;
    private final LetterTemplateRepository letterTemplateRepository;

    public ApplyService(
            HhGateway hhGateway,
            HhAccountService hhAccountService,
            RateLimiter rateLimiter,
            ApplicationLogRepository applicationLogRepository,
            LetterTemplateRepository letterTemplateRepository, BotUserRepository users, ResponseRuleRepository rules) {
        this.users = users;
        this.rules = rules;
        this.hhGateway = hhGateway;
        this.hhAccountService = hhAccountService;
        this.rateLimiter = rateLimiter;
        this.applicationLogRepository = applicationLogRepository;
        this.letterTemplateRepository = letterTemplateRepository;
    }

    private final com.antonk404.hhbot.service.util.UserLocks locks = new com.antonk404.hhbot.service.util.UserLocks();

    public ApplyResult apply(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        return locks.withLock(user.getId(), () -> applyCurrent(user.getId(), rule.getId(), vacancy, null));
    }

    public ApplyResult applyAutomatically(BotUser user, ResponseRule rule, HhVacancy vacancy, String expected) {
        return locks.withLock(user.getId(), () -> applyCurrent(user.getId(), rule.getId(), vacancy, expected));
    }

    private ApplyResult applyCurrent(Long userId, Long ruleId, HhVacancy vacancy, String expected) {
        BotUser user = users.findById(userId).orElse(null);
        if (user == null) return new ApplyResult.Deferred("Профиль удалён");
        ResponseRule rule = rules.findByIdAndUserId(ruleId, user.getId()).orElse(null);
        if (rule == null || !rule.isEnabled() || !rule.isReady())
            return new ApplyResult.Deferred("Правило выключено или удалено");
        if (expected != null && (rule.getMode() != com.antonk404.hhbot.domain.RuleMode.AUTO
                || !expected.equals(VacancyScanner.fingerprint(rule))))
            return new ApplyResult.Deferred("Настройки правила изменились — обновляю очередь");
        if (user.isPaused()) return new ApplyResult.Deferred("Отклики на паузе");

        var stop = rateLimiter.stop(user.getId());
        if (stop.isPresent()) return stop.get() == RateLimiter.Stop.CAPTCHA
                ? new ApplyResult.Captcha() : new ApplyResult.LimitReached();
        if (rateLimiter.exhausted(user.getId())) return new ApplyResult.Deferred("Сегодня отправлено 100 откликов. Продолжу завтра");
        if (rateLimiter.coolingDown(user.getId())) return new ApplyResult.Deferred("Пауза между откликами — попробуй через минуту");
        return applyChecked(user, rule, vacancy);
    }

    private ApplyResult applyChecked(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        if (applicationLogRepository.existsByUserIdAndVacancyId(user.getId(), vacancy.id())) {
            return new ApplyResult.AlreadyApplied();
        }
        if (vacancy.hasTest()) {
            log(user, rule, vacancy, ApplicationState.MANUAL, "тест работодателя");
            return new ApplyResult.NeedsTest();
        }
        Optional<HhSessionCookies> cookies = hhAccountService.cookies(user.getId());
        if (cookies.isEmpty()) {
            return new ApplyResult.SessionExpired();
        }
        String letter = letter(user, rule, vacancy);
        if (letter.isEmpty() && vacancy.letterRequired()) {
            // Не идём в hh за заведомым отказом: каждый лишний запрос отклика - это запрос отклика.
            return new ApplyResult.Failed("работодатель требует письмо, а в правиле его нет");
        }

        rateLimiter.recordAttempt(user.getId());
        ApplyResult result = hhGateway.apply(cookies.get(), vacancy.id(), rule.getResumeHash(), letter);
        switch (result) {
            case ApplyResult.Sent sent -> {
                log(user, rule, vacancy, ApplicationState.SENT, null);
            }
            case ApplyResult.AlreadyApplied already ->
                    log(user, rule, vacancy, ApplicationState.SKIPPED, "отклик уже был на hh");
            case ApplyResult.NeedsTest test ->
                    log(user, rule, vacancy, ApplicationState.MANUAL, "тест работодателя");
            case ApplyResult.LimitReached limit ->
                    rateLimiter.stopUntilTomorrow(user.getId(), RateLimiter.Stop.HH_LIMIT);
            case ApplyResult.Captcha captcha ->
                    rateLimiter.stopUntilTomorrow(user.getId(), RateLimiter.Stop.CAPTCHA);
            case ApplyResult.SessionExpired expired -> hhAccountService.markExpired(user.getId());
            // Не в журнал: сбой сети не должен навсегда закрывать вакансию для повторной попытки.
            case ApplyResult.Deferred deferred -> {}
            case ApplyResult.Failed failed -> {
            }
        }
        return result;
    }

    public void skip(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        locks.withLock(user.getId(), () -> {
            if (!applicationLogRepository.existsByUserIdAndVacancyId(user.getId(), vacancy.id()))
                log(user, rule, vacancy, ApplicationState.SKIPPED, "пропущено вручную");
            return null;
        });
    }

    /** Письмо по шаблону правила; пустая строка, если шаблона нет. */
    public String letter(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        if (rule.getLetterTemplateId() == null) {
            return "";
        }
        String ownerName = hhAccountService.session(user.getId()).map(session -> session.getOwnerName()).orElse("");
        return letterTemplateRepository.findByIdAndUserId(rule.getLetterTemplateId(), user.getId())
                .map(LetterTemplate::getBody)
                .map(body -> LetterRenderer.render(body, vacancy, ownerName))
                .orElse("");
    }

    private void log(BotUser user, ResponseRule rule, HhVacancy vacancy, ApplicationState state, String reason) {
        applicationLogRepository.save(new ApplicationLog(
                user.getId(), rule.getId(), vacancy.id(), vacancy.name(), vacancy.company(), state, reason));
    }
}
