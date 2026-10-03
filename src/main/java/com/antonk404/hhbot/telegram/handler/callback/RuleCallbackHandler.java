package com.antonk404.hhbot.telegram.handler.callback;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.VacancyScanner;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.DialogState.Step;
import com.antonk404.hhbot.telegram.state.MenuMessage;
import com.antonk404.hhbot.telegram.view.Cb;
import com.antonk404.hhbot.telegram.view.MenuScreens;
import com.antonk404.hhbot.telegram.view.RuleScreens;
import com.antonk404.hhbot.telegram.view.Screen;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;
import java.util.Optional;

/** Раздел «Правила»: список, мастер создания, экран правила и его настройки. */
@Component
public class RuleCallbackHandler implements CallbackHandler {

    /** Сколько вакансий показывать в «Проверить сейчас»: хватает оценить выдачу, влезает в сообщение. */
    private static final int PREVIEW_SIZE = 8;

    public static final String NEW_RULE_PROMPT = """
            <b>Новое правило · шаг 1 из 4</b>

            Как его назвать? Название видишь только ты.

            Например: <code>Java, удалёнка</code>""";

    private final Access access;
    private final ResponseRuleRepository responseRuleRepository;
    private final LetterTemplateRepository letterTemplateRepository;
    private final HhAccountService hhAccountService;
    private final VacancyScanner vacancyScanner;
    private final DialogState dialogState;
    private final MenuMessage menu;
    private final RuleScreens screens;

    public RuleCallbackHandler(
            Access access,
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository,
            HhAccountService hhAccountService,
            VacancyScanner vacancyScanner,
            DialogState dialogState,
            MenuMessage menu,
            RuleScreens screens) {
        this.access = access;
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
        this.hhAccountService = hhAccountService;
        this.vacancyScanner = vacancyScanner;
        this.dialogState = dialogState;
        this.menu = menu;
        this.screens = screens;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith("r:");
    }

    @Override
    public void handle(CallbackQuery query) {
        access.press(query).ifPresent(this::handle);
    }

    private void handle(Access.Press press) {
        BotUser user = press.user();
        dialogState.clear(user.getId());

        if (press.data().equals(Cb.RULES)) {
            menu.edit(press, screens.list(user));
            return;
        }
        if (press.data().equals(Cb.RULE_NEW)) {
            dialogState.expect(user.getId(), Step.RULE_NEW);
            menu.edit(press, MenuScreens.prompt(NEW_RULE_PROMPT, Cb.RULES));
            return;
        }

        // r:<id>:<action>[:<arg>]
        String[] parts = press.data().split(":", 4);
        Optional<ResponseRule> found = parts.length < 3 ? Optional.empty() : parseLong(parts[1])
                .flatMap(id -> responseRuleRepository.findByIdAndUserId(id, user.getId()));
        if (found.isEmpty()) {
            menu.edit(press, screens.list(user), "Этого правила уже нет");
            return;
        }
        ResponseRule rule = found.get();
        String arg = parts.length == 4 ? parts[3] : "";

        switch (parts[2]) {
            case RuleScreens.SEARCH -> menu.edit(press, screens.search(rule));
            case RuleScreens.REPLY -> menu.edit(press, screens.reply(rule));
            case RuleScreens.HOW -> menu.edit(press, screens.how(rule));

            case "kw" -> ask(press, rule, Step.RULE_KEYWORDS, RuleScreens.SEARCH, """
                    <b>Ключевые слова</b>

                    Напиши так, как искал бы на hh. Например: <code>java разработчик</code>

                    Можно точнее: <code>java OR kotlin</code>, <code>"team lead"</code>, \
                    <code>python NOT стажёр</code>""");
            case "minus" -> ask(press, rule, Step.RULE_MINUS, RuleScreens.SEARCH, """
                    <b>Минус-слова</b>

                    Через запятую. Вакансию с любым из них в названии я пропущу.

                    Например: <code>senior, lead, стажёр</code>

                    Чтобы очистить, пришли <code>-</code>""");
            case "bl" -> ask(press, rule, Step.RULE_BLACKLIST, RuleScreens.SEARCH, """
                    <b>Стоп-лист компаний</b>

                    Через запятую, достаточно части названия. Этим компаниям откликаться не буду.

                    Например: <code>кадровое агентство, аутстафф</code>

                    Чтобы очистить, пришли <code>-</code>""");
            case "name" -> ask(press, rule, Step.RULE_NAME, "show", "<b>Новое название правила</b>");
            case "areaid" -> ask(press, rule, Step.RULE_AREA, RuleScreens.SEARCH, """
                    <b>Другой город</b>

                    Нужен номер региона hh. Открой поиск на hh.ru, выбери город и посмотри в адресе \
                    страницы: <code>area=88</code> - это Казань, пришли <code>88</code>.""");
            case "salx" -> ask(press, rule, Step.RULE_SALARY, RuleScreens.SEARCH,
                    "<b>Зарплата от</b>\n\nЧисло в рублях. Например: <code>180000</code>");
            case "limx" -> ask(press, rule, Step.RULE_LIMIT, RuleScreens.HOW,
                    "<b>Лимит откликов в день</b>\n\nЧисло от 1 до 100.");

            case "area" -> {
                if (arg.isEmpty()) {
                    menu.edit(press, screens.area(rule));
                } else {
                    int areaId = parseLong(arg).orElse(0L).intValue();
                    rule.setArea(areaId == 0 ? null : areaId, RuleScreens.AREAS.get(areaId));
                    save(press, rule, screens.search(rule));
                }
            }
            case "exp" -> {
                if (arg.isEmpty()) {
                    menu.edit(press, screens.experience(rule));
                } else if (arg.equals("any") || RuleScreens.EXPERIENCE.containsKey(arg)) {
                    rule.setExperience(arg.equals("any") ? null : arg);
                    save(press, rule, screens.search(rule));
                }
            }
            case "sal" -> {
                if (arg.isEmpty()) {
                    menu.edit(press, screens.salary(rule));
                } else {
                    int amount = parseLong(arg).orElse(0L).intValue();
                    rule.setSalaryFrom(amount <= 0 ? null : amount);
                    save(press, rule, screens.search(rule));
                }
            }
            case "lim" -> {
                if (arg.isEmpty()) {
                    menu.edit(press, screens.limit(rule));
                } else {
                    // Потолок тот же, что при вводе руками: data приходит от клиента.
                    rule.setDailyLimit((int) Math.clamp(parseLong(arg).orElse(30L), 1L, 100L));
                    save(press, rule, screens.how(rule));
                }
            }
            case "remote" -> {
                rule.setRemoteOnly(!rule.isRemoteOnly());
                save(press, rule, screens.search(rule));
            }
            case "title" -> {
                rule.setTitleOnly(!rule.isTitleOnly());
                save(press, rule, screens.search(rule));
            }
            case "mode" -> {
                rule.setMode(rule.getMode() == RuleMode.AUTO ? RuleMode.CONFIRM : RuleMode.AUTO);
                save(press, rule, screens.how(rule));
            }

            case "resume" -> resumes(press, rule).ifPresent(list -> menu.finish(press, screens.resumes(rule, list, false)));
            case "rs" -> resumes(press, rule).ifPresent(list -> {
                pickResume(rule, list, arg);
                menu.finish(press, screens.reply(rule));
            });
            case "letter" -> menu.edit(press, screens.letters(user, rule));
            case "lt" -> {
                Optional<Long> templateId = parseLong(arg).filter(value -> value != 0);
                // Чужой шаблон не привязать: id приходит из data, проверяем владельца.
                if (templateId.isPresent()
                        && letterTemplateRepository.findByIdAndUserId(templateId.get(), user.getId()).isEmpty()) {
                    menu.edit(press, screens.letters(user, rule), "Этого письма уже нет");
                    return;
                }
                rule.setLetterTemplateId(templateId.orElse(null));
                save(press, rule, screens.reply(rule));
            }

            case "wrs" -> resumes(press, rule).ifPresent(list -> {
                pickResume(rule, list, arg);
                menu.finish(press, screens.wizardMode(rule));
            });
            case "wskip" -> menu.edit(press, screens.wizardMode(rule));
            case "wm" -> {
                rule.setMode(arg.equals("auto") ? RuleMode.AUTO : RuleMode.CONFIRM);
                responseRuleRepository.save(rule);
                menu.edit(press, screens.wizardDone(rule));
            }

            case "toggle" -> toggle(press, rule);
            case "test" -> preview(press, rule);
            case "del" -> menu.edit(press, screens.delete(rule));
            case "delok" -> {
                responseRuleRepository.delete(rule);
                menu.edit(press, screens.list(user), "Удалил");
            }
            default -> menu.edit(press, screens.rule(rule));
        }
    }

    private void toggle(Access.Press press, ResponseRule rule) {
        Long id = rule.getId();
        if (!rule.isEnabled()) {
            if (rule.getKeywords() == null || rule.getKeywords().isBlank()) {
                menu.edit(press, MenuScreens.problem("Не могу включить: не заданы ключевые слова.",
                        "🔎 Задать слова", Cb.rule(id, "kw"), Cb.rule(id, "show")));
                return;
            }
            if (rule.getResumeHash() == null) {
                menu.edit(press, MenuScreens.problem("Не могу включить: не выбрано резюме.",
                        "📄 Выбрать резюме", Cb.rule(id, "resume"), Cb.rule(id, "show")));
                return;
            }
            if (hhAccountService.cookies(press.user().getId()).isEmpty()) {
                menu.edit(press, noAccount(id));
                return;
            }
        }
        rule.setEnabled(!rule.isEnabled());
        responseRuleRepository.save(rule);
        menu.edit(press, screens.rule(rule), rule.isEnabled() ? "Включил" : "Выключил");
    }

    private void preview(Access.Press press, ResponseRule rule) {
        Long id = rule.getId();
        if (rule.getKeywords() == null || rule.getKeywords().isBlank()) {
            menu.edit(press, MenuScreens.problem("Нечего проверять: не заданы ключевые слова.",
                    "🔎 Задать слова", Cb.rule(id, "kw"), Cb.rule(id, "show")));
            return;
        }
        menu.progress(press, "Ищу вакансии на hh…");
        try {
            List<HhVacancy> vacancies = vacancyScanner.preview(press.user(), rule);
            menu.finish(press, screens.preview(rule, vacancies, PREVIEW_SIZE));
        } catch (HhSessionExpiredException e) {
            menu.finish(press, noAccount(id));
        } catch (HhException e) {
            menu.finish(press, hhSilent(Cb.rule(id, "test"), id));
        }
    }

    /**
     * Резюме с hh. Пока hh отвечает, на месте меню висит «жду»; при сбое метод сам рисует экран
     * с тем, что делать дальше, и возвращает пусто.
     */
    private Optional<List<HhResume>> resumes(Access.Press press, ResponseRule rule) {
        menu.progress(press, "Спрашиваю у hh твои резюме…");
        try {
            return Optional.of(hhAccountService.profile(press.user().getId()).resumes());
        } catch (HhSessionExpiredException e) {
            menu.finish(press, noAccount(rule.getId()));
        } catch (HhException e) {
            menu.finish(press, hhSilent(Cb.rule(rule.getId(), "resume"), rule.getId()));
        }
        return Optional.empty();
    }

    /** Хеш приходит из data - берём только то резюме, которое hh сейчас действительно отдаёт. */
    private void pickResume(ResponseRule rule, List<HhResume> resumes, String hash) {
        resumes.stream()
                .filter(resume -> resume.hash().equals(hash))
                .findFirst()
                .ifPresent(resume -> {
                    rule.setResume(resume.hash(), resume.title());
                    responseRuleRepository.save(rule);
                });
    }

    private static Screen noAccount(Long ruleId) {
        return MenuScreens.problem("Аккаунт hh не подключён или сессия истекла.",
                "🔑 Подключить hh", Cb.CONNECT, Cb.rule(ruleId, "show"));
    }

    private static Screen hhSilent(String retryData, Long ruleId) {
        return MenuScreens.problem("hh сейчас не отвечает.", "🔄 Попробовать ещё раз",
                retryData, Cb.rule(ruleId, "show"));
    }

    private void ask(Access.Press press, ResponseRule rule, Step step, String backAction, String question) {
        dialogState.expect(press.user().getId(), step, rule.getId());
        menu.edit(press, MenuScreens.prompt(question, Cb.rule(rule.getId(), backAction)));
    }

    private void save(Access.Press press, ResponseRule rule, Screen next) {
        responseRuleRepository.save(rule);
        menu.edit(press, next, "Сохранил");
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
