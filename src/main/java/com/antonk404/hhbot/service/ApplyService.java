package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.ApplicationLog;
import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
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
            LetterTemplateRepository letterTemplateRepository) {
        this.hhGateway = hhGateway;
        this.hhAccountService = hhAccountService;
        this.rateLimiter = rateLimiter;
        this.applicationLogRepository = applicationLogRepository;
        this.letterTemplateRepository = letterTemplateRepository;
    }

    public ApplyResult apply(BotUser user, ResponseRule rule, HhVacancy vacancy) {
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

        ApplyResult result = hhGateway.apply(cookies.get(), vacancy.id(), rule.getResumeHash(), letter);
        switch (result) {
            case ApplyResult.Sent sent -> {
                rateLimiter.recordSent(rule);
                log(user, rule, vacancy, ApplicationState.SENT, null);
            }
            case ApplyResult.AlreadyApplied already ->
                    log(user, rule, vacancy, ApplicationState.SKIPPED, "отклик уже был на hh");
            case ApplyResult.NeedsTest test ->
                    log(user, rule, vacancy, ApplicationState.MANUAL, "тест работодателя");
            case ApplyResult.LimitReached limit ->
                    rateLimiter.stopUntilTomorrow(user.getId(), "суточный лимит hh");
            case ApplyResult.Captcha captcha ->
                    rateLimiter.stopUntilTomorrow(user.getId(), "капча");
            case ApplyResult.SessionExpired expired -> hhAccountService.markExpired(user.getId());
            // Не в журнал: сбой сети не должен навсегда закрывать вакансию для повторной попытки.
            case ApplyResult.Failed failed -> {
            }
        }
        return result;
    }

    public void skip(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        if (!applicationLogRepository.existsByUserIdAndVacancyId(user.getId(), vacancy.id())) {
            log(user, rule, vacancy, ApplicationState.SKIPPED, "пропущено вручную");
        }
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
