package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.dto.RuleData;
import com.antonk404.hhbot.domain.dto.RuleView;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.exceptions.NotFoundException;
import com.antonk404.hhbot.service.exceptions.ValidationException;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Правила отклика для мини-приложения: проверка того, что прислала форма, и владельца на каждом
 * обращении. Все проверки здесь, а не в контроллере: форма - не единственное, что может прислать
 * запрос с валидной подписью.
 */
@Service
public class RuleService {

    private static final int MAX_NAME = 64;
    private static final int MAX_TEXT = 500;

    private final ResponseRuleRepository responseRuleRepository;
    private final LetterTemplateRepository letterTemplateRepository;
    private final HhAccountService hhAccountService;
    private final VacancyScanner vacancyScanner;
    private final RateLimiter rateLimiter;

    public RuleService(
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository,
            HhAccountService hhAccountService,
            VacancyScanner vacancyScanner,
            RateLimiter rateLimiter) {
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
        this.hhAccountService = hhAccountService;
        this.vacancyScanner = vacancyScanner;
        this.rateLimiter = rateLimiter;
    }

    public List<RuleView> list(BotUser user) {
        return responseRuleRepository.findByUserIdOrderByIdAsc(user.getId()).stream().map(this::view).toList();
    }

    public RuleView get(BotUser user, Long id) {
        return view(find(user, id));
    }

    /** Новое правило всегда выключено: включение - отдельное осознанное действие. */
    public RuleView create(BotUser user, RuleData data) {
        ResponseRule rule = new ResponseRule(user.getId(), name(data.name()));
        apply(user, rule, data);
        return view(responseRuleRepository.save(rule));
    }

    public RuleView update(BotUser user, Long id, RuleData data) {
        ResponseRule rule = find(user, id);
        rule.setName(name(data.name()));
        apply(user, rule, data);
        // Правка могла сделать включённое правило неполным - например, стёрли ключевые слова.
        if (rule.isEnabled() && !rule.isReady()) {
            rule.setEnabled(false);
        }
        return view(responseRuleRepository.save(rule));
    }

    public RuleView setEnabled(BotUser user, Long id, boolean enabled) {
        ResponseRule rule = find(user, id);
        if (enabled) {
            if (blank(rule.getKeywords())) {
                throw new ValidationException("keywords", "Чтобы включить правило, задай ключевые слова.");
            }
            if (rule.getResumeHash() == null) {
                throw new ValidationException("resumeHash", "Чтобы включить правило, выбери резюме.");
            }
            if (hhAccountService.cookies(user.getId()).isEmpty()) {
                throw new ValidationException(null, "Сначала подключи аккаунт hh.");
            }
        }
        rule.setEnabled(enabled);
        return view(responseRuleRepository.save(rule));
    }

    public void delete(BotUser user, Long id) {
        responseRuleRepository.delete(find(user, id));
    }

    /** Что правило найдёт прямо сейчас. Ничего не отправляет и ничего не помечает просмотренным. */
    public List<HhVacancy> preview(BotUser user, Long id) {
        ResponseRule rule = find(user, id);
        if (blank(rule.getKeywords())) {
            throw new ValidationException("keywords", "Сначала задай ключевые слова.");
        }
        return vacancyScanner.preview(user, rule);
    }

    private void apply(BotUser user, ResponseRule rule, RuleData data) {
        rule.setKeywords(text("keywords", data.keywords()));
        rule.setMinusWords(text("minusWords", data.minusWords()));
        rule.setCompanyBlacklist(text("companyBlacklist", data.companyBlacklist()));
        rule.setTitleOnly(data.titleOnly() == null || data.titleOnly());
        rule.setRemoteOnly(Boolean.TRUE.equals(data.remoteOnly()));
        rule.setMode(data.mode() == null ? RuleMode.CONFIRM : data.mode());

        if (data.areaId() != null && data.areaId() <= 0) {
            throw new ValidationException("areaId", "Регион задаётся положительным номером.");
        }
        rule.setAreaId(data.areaId());

        if (data.salaryFrom() != null && (data.salaryFrom() <= 0 || data.salaryFrom() > 100_000_000)) {
            throw new ValidationException("salaryFrom", "Зарплата - число в рублях больше нуля.");
        }
        rule.setSalaryFrom(data.salaryFrom());

        if (data.experience() != null && !ResponseRule.EXPERIENCE_CODES.contains(data.experience())) {
            throw new ValidationException("experience", "Неизвестный код опыта.");
        }
        rule.setExperience(data.experience());

        int limit = data.dailyLimit() == null ? ResponseRule.DEFAULT_DAILY_LIMIT : data.dailyLimit();
        if (limit < 1 || limit > ResponseRule.MAX_DAILY_LIMIT) {
            throw new ValidationException("dailyLimit", "Лимит - от 1 до " + ResponseRule.MAX_DAILY_LIMIT + " откликов в день.");
        }
        rule.setDailyLimit(limit);

        if (data.letterTemplateId() != null
                && letterTemplateRepository.findByIdAndUserId(data.letterTemplateId(), user.getId()).isEmpty()) {
            throw new ValidationException("letterTemplateId", "Этого письма уже нет.");
        }
        rule.setLetterTemplateId(data.letterTemplateId());

        resume(user, rule, data.resumeHash());
    }

    /**
     * В hh идём, только если резюме действительно поменяли: иначе каждое сохранение формы
     * стоило бы запроса к сайту и падало бы вместе с ним.
     */
    private void resume(BotUser user, ResponseRule rule, String hash) {
        if (Objects.equals(hash, rule.getResumeHash())) {
            return;
        }
        if (hash == null) {
            rule.setResume(null, null);
            return;
        }
        HhResume resume = hhAccountService.profile(user.getId()).resumes().stream()
                .filter(candidate -> candidate.hash().equals(hash))
                .findFirst()
                .orElseThrow(() -> new ValidationException("resumeHash", "Такого резюме на hh нет."));
        rule.setResume(resume.hash(), resume.title());
    }

    private ResponseRule find(BotUser user, Long id) {
        return responseRuleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Правила уже нет."));
    }

    private RuleView view(ResponseRule rule) {
        return RuleView.of(rule, rateLimiter.sentToday(rule));
    }

    private static String name(String value) {
        if (blank(value)) {
            throw new ValidationException("name", "У правила должно быть название.");
        }
        if (value.strip().length() > MAX_NAME) {
            throw new ValidationException("name", "Название - не длиннее " + MAX_NAME + " символов.");
        }
        return value.strip();
    }

    /** Пустая строка и строка из пробелов хранятся как null - «не задано» должно быть одно. */
    private static String text(String field, String value) {
        if (blank(value)) {
            return null;
        }
        if (value.strip().length() > MAX_TEXT) {
            throw new ValidationException(field, "Не длиннее " + MAX_TEXT + " символов.");
        }
        return value.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
