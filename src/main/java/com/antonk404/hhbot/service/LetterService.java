package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.dto.LetterData;
import com.antonk404.hhbot.domain.dto.LetterPreview;
import com.antonk404.hhbot.domain.dto.LetterView;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.service.exceptions.NotFoundException;
import com.antonk404.hhbot.service.exceptions.ValidationException;
import com.antonk404.hhbot.service.util.LetterRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Шаблоны сопроводительных писем для мини-приложения. */
@Service
public class LetterService {

    private static final int MAX_NAME = 64;

    /** На чём показать предпросмотр, если настоящую вакансию достать не вышло. */
    private static final HhVacancy SAMPLE = new HhVacancy(
            "0", "Java-разработчик", "Рога и копыта", "от 200 000 до 300 000 ₽", "Москва", "", false, false);

    private final LetterTemplateRepository letterTemplateRepository;
    private final ResponseRuleRepository responseRuleRepository;
    private final HhAccountService hhAccountService;
    private final VacancyScanner vacancyScanner;

    public LetterService(
            LetterTemplateRepository letterTemplateRepository,
            ResponseRuleRepository responseRuleRepository,
            HhAccountService hhAccountService,
            VacancyScanner vacancyScanner) {
        this.letterTemplateRepository = letterTemplateRepository;
        this.responseRuleRepository = responseRuleRepository;
        this.hhAccountService = hhAccountService;
        this.vacancyScanner = vacancyScanner;
    }

    public List<LetterView> list(BotUser user) {
        return letterTemplateRepository.findByUserIdOrderByIdAsc(user.getId()).stream().map(LetterView::of).toList();
    }

    public LetterView create(BotUser user, LetterData data) {
        return LetterView.of(letterTemplateRepository.save(
                new LetterTemplate(user.getId(), name(data.name()), body(data.body()))));
    }

    public LetterView update(BotUser user, Long id, LetterData data) {
        LetterTemplate template = find(user, id);
        template.setName(name(data.name()));
        template.setBody(body(data.body()));
        return LetterView.of(letterTemplateRepository.save(template));
    }

    /** Правила, где письмо было выбрано, остаются рабочими - просто без письма. */
    @Transactional
    public void delete(BotUser user, Long id) {
        LetterTemplate template = find(user, id);
        for (ResponseRule rule : responseRuleRepository.findByLetterTemplateId(template.getId())) {
            rule.setLetterTemplateId(null);
            responseRuleRepository.save(rule);
        }
        letterTemplateRepository.delete(template);
    }

    /**
     * Как текст уйдёт работодателю. Принимает сам текст, а не id: предпросмотр нужен в форме до
     * сохранения, пока человек ещё пишет.
     */
    public LetterPreview preview(BotUser user, String body) {
        String checked = body(body);
        Optional<HhVacancy> real = realVacancy(user);
        String ownerName = hhAccountService.session(user.getId()).map(HhSession::getOwnerName).orElse("");
        HhVacancy vacancy = real.orElse(SAMPLE);
        return new LetterPreview(LetterRenderer.render(checked, vacancy, ownerName), vacancy, real.isPresent());
    }

    /** Первая вакансия по первому правилу с ключевыми словами; пусто, если такой нет или hh молчит. */
    private Optional<HhVacancy> realVacancy(BotUser user) {
        Optional<ResponseRule> rule = responseRuleRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
                .filter(candidate -> candidate.getKeywords() != null && !candidate.getKeywords().isBlank())
                .findFirst();
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        try {
            return vacancyScanner.preview(user, rule.get()).stream().findFirst();
        } catch (HhException e) {
            return Optional.empty();
        }
    }

    private LetterTemplate find(BotUser user, Long id) {
        return letterTemplateRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Письма уже нет."));
    }

    private static String name(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("name", "У письма должно быть название.");
        }
        if (value.strip().length() > MAX_NAME) {
            throw new ValidationException("name", "Название - не длиннее " + MAX_NAME + " символов.");
        }
        return value.strip();
    }

    private static String body(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("body", "Письмо не может быть пустым.");
        }
        String text = value.strip();
        if (text.length() > LetterTemplate.MAX_BODY) {
            throw new ValidationException("body", "Письмо - не длиннее " + LetterTemplate.MAX_BODY + " символов.");
        }
        List<String> unknown = LetterRenderer.unknownAliases(text);
        if (!unknown.isEmpty()) {
            // Опечатка в алиасе иначе уехала бы работодателю дословно.
            throw new ValidationException("body", "Не знаю, что подставить вместо [" + String.join("], [", unknown)
                    + "]. Доступны: [" + String.join("], [", LetterRenderer.ALIASES) + "].");
        }
        return text;
    }
}
