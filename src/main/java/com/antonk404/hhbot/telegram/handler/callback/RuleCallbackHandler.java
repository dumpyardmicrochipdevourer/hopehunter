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
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.DialogState.Step;
import com.antonk404.hhbot.telegram.view.Kb;
import com.antonk404.hhbot.telegram.view.Screen;
import com.antonk404.hhbot.telegram.view.Screens;
import com.antonk404.hhbot.telegram.view.VacancyPresenter;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;
import java.util.Optional;

/** Раздел «Правила»: список, экран правила и все его кнопки. Data: {@code r:<id>:<действие>[:<аргумент>]}. */
@Component
public class RuleCallbackHandler implements CallbackHandler {

    /** Сколько вакансий показывать в «Проверить сейчас»: хватает оценить выдачу, влезает в сообщение. */
    private static final int PREVIEW_SIZE = 10;

    private final Access access;
    private final ResponseRuleRepository responseRuleRepository;
    private final LetterTemplateRepository letterTemplateRepository;
    private final HhAccountService hhAccountService;
    private final VacancyScanner vacancyScanner;
    private final DialogState dialogState;
    private final Screens screens;
    private final TelegramReplies replies;

    public RuleCallbackHandler(
            Access access,
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository,
            HhAccountService hhAccountService,
            VacancyScanner vacancyScanner,
            DialogState dialogState,
            Screens screens,
            TelegramReplies replies) {
        this.access = access;
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
        this.hhAccountService = hhAccountService;
        this.vacancyScanner = vacancyScanner;
        this.dialogState = dialogState;
        this.screens = screens;
        this.replies = replies;
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

        if (press.data().equals(Screens.RULES)) {
            show(press, screens.rules(user));
            return;
        }
        if (press.data().equals(Screens.RULE_NEW)) {
            dialogState.expect(user.getId(), Step.RULE_NEW);
            show(press, screens.prompt("Как назвать правило? Например: «Java, удалёнка».", Screens.RULES));
            return;
        }

        // r:<id>:<action>[:<arg>]
        String[] parts = press.data().split(":", 4);
        Optional<ResponseRule> found = parts.length < 3 ? Optional.empty() : parseId(parts[1])
                .flatMap(id -> responseRuleRepository.findByIdAndUserId(id, user.getId()));
        if (found.isEmpty()) {
            replies.answerCallback(press.queryId(), "правила уже нет");
            replies.editMenu(press.chatId(), press.messageId(), screens.rules(user));
            return;
        }
        ResponseRule rule = found.get();
        String arg = parts.length == 4 ? parts[3] : "";

        switch (parts[2]) {
            case "kw" -> ask(press, rule, Step.RULE_KEYWORDS,
                    "Ключевые слова - как в строке поиска hh. Работает язык запросов: "
                            + "java OR kotlin, \"team lead\", NOT стажёр.");
            case "minus" -> ask(press, rule, Step.RULE_MINUS,
                    "Минус-слова через запятую. Вакансия с любым из них в названии будет отброшена.\n"
                            + "Например: senior, lead, 1с\n\nПришли «-», чтобы очистить.");
            case "bl" -> ask(press, rule, Step.RULE_BLACKLIST,
                    "Компании, которым не откликаться, через запятую. Достаточно части названия.\n\n"
                            + "Пришли «-», чтобы очистить.");
            case "salary" -> ask(press, rule, Step.RULE_SALARY,
                    "Зарплата от, числом в рублях. Вакансии без указанной вилки при этом не попадут в выдачу.\n\n"
                            + "Пришли «-», чтобы снять порог.");
            case "limit" -> ask(press, rule, Step.RULE_LIMIT,
                    "Сколько откликов в день по этому правилу, от 1 до 100. Чем меньше, тем спокойнее "
                            + "для аккаунта.");
            case "name" -> ask(press, rule, Step.RULE_NAME, "Новое название правила:");
            case "areaid" -> ask(press, rule, Step.RULE_AREA,
                    "Id региона hh, числом. Его видно в адресе поиска на сайте: …&area=88 - это Казань.");
            case "area" -> {
                if (!arg.isEmpty()) {
                    int areaId = Integer.parseInt(arg);
                    rule.setAreaId(areaId == 0 ? null : areaId);
                    save(press, rule);
                } else {
                    show(press, screens.ruleArea(rule));
                }
            }
            case "exp" -> {
                if (arg.isEmpty()) {
                    show(press, screens.ruleExperience(rule));
                } else if (arg.equals("any") || Screens.EXPERIENCE.containsKey(arg)) {
                    rule.setExperience(arg.equals("any") ? null : arg);
                    save(press, rule);
                }
            }
            case "remote" -> {
                rule.setRemoteOnly(!rule.isRemoteOnly());
                save(press, rule);
            }
            case "title" -> {
                rule.setTitleOnly(!rule.isTitleOnly());
                save(press, rule);
            }
            case "mode" -> {
                rule.setMode(rule.getMode() == RuleMode.AUTO ? RuleMode.CONFIRM : RuleMode.AUTO);
                save(press, rule);
            }
            case "resume" -> resumes(press, rule).ifPresent(list -> show(press, screens.ruleResumes(rule, list)));
            case "rs" -> resumes(press, rule).ifPresent(list -> list.stream()
                    .filter(resume -> resume.hash().equals(arg))
                    .findFirst()
                    .ifPresentOrElse(resume -> {
                        rule.setResume(resume.hash(), resume.title());
                        save(press, rule);
                    }, () -> replies.alert(press.queryId(), "Такого резюме на hh уже нет.")));
            case "letter" -> show(press, screens.ruleLetters(user, rule));
            case "lt" -> {
                Optional<Long> templateId = parseId(arg).filter(id -> id != 0);
                // Чужой шаблон не привязать: id приходит из data, проверяем владельца.
                if (templateId.isPresent()
                        && letterTemplateRepository.findByIdAndUserId(templateId.get(), user.getId()).isEmpty()) {
                    replies.answerCallback(press.queryId(), "письма уже нет");
                    return;
                }
                rule.setLetterTemplateId(templateId.orElse(null));
                save(press, rule);
            }
            case "toggle" -> toggle(press, rule);
            case "test" -> preview(press, rule);
            case "del" -> show(press, screens.ruleDelete(rule));
            case "delok" -> {
                responseRuleRepository.delete(rule);
                replies.answerCallback(press.queryId(), "удалил");
                replies.editMenu(press.chatId(), press.messageId(), screens.rules(user));
            }
            default -> show(press, screens.rule(rule));
        }
    }

    private void toggle(Access.Press press, ResponseRule rule) {
        if (!rule.isEnabled()) {
            if (!rule.isReady()) {
                replies.alert(press.queryId(), "Сначала задай ключевые слова и выбери резюме.");
                return;
            }
            if (hhAccountService.cookies(press.user().getId()).isEmpty()) {
                replies.alert(press.queryId(), "Сначала подключи аккаунт hh.");
                return;
            }
        }
        rule.setEnabled(!rule.isEnabled());
        save(press, rule);
    }

    private void preview(Access.Press press, ResponseRule rule) {
        if (rule.getKeywords() == null || rule.getKeywords().isBlank()) {
            replies.alert(press.queryId(), "Сначала задай ключевые слова.");
            return;
        }
        List<HhVacancy> vacancies;
        try {
            vacancies = vacancyScanner.preview(press.user(), rule);
        } catch (HhSessionExpiredException e) {
            replies.alert(press.queryId(), "Сначала подключи аккаунт hh.");
            return;
        } catch (HhException e) {
            replies.alert(press.queryId(), "hh не ответил, попробуй позже.");
            return;
        }
        replies.answerCallback(press.queryId(), null);

        StringBuilder text = new StringBuilder("Проверка правила «").append(rule.getName())
                .append("». Ничего не отправлено.\n");
        if (vacancies.isEmpty()) {
            text.append("\nНа первой странице выдачи ничего не подошло. Попробуй ослабить фильтры.");
        } else {
            text.append("Подходит на первой странице: ").append(vacancies.size()).append(".\n");
        }
        vacancies.stream().limit(PREVIEW_SIZE).forEach(vacancy ->
                text.append('\n').append(VacancyPresenter.describe(vacancy)).append('\n')
                        .append(vacancy.hasTest() ? "(с тестом - пришлю ссылкой)\n" : "")
                        .append(vacancy.url()).append('\n'));
        replies.editMenu(press.chatId(), press.messageId(), new Screen(text.toString(),
                Kb.of().row(Kb.btn("« Назад", Screens.rule(rule.getId(), "show"))).build()));
    }

    /** Резюме с hh; при сбое сам отвечает человеку и возвращает пусто. */
    private Optional<List<HhResume>> resumes(Access.Press press, ResponseRule rule) {
        try {
            return Optional.of(hhAccountService.profile(press.user().getId()).resumes());
        } catch (HhSessionExpiredException e) {
            replies.alert(press.queryId(), "Сначала подключи аккаунт hh.");
        } catch (HhException e) {
            replies.alert(press.queryId(), "hh не ответил, попробуй позже.");
        }
        return Optional.empty();
    }

    private void ask(Access.Press press, ResponseRule rule, Step step, String question) {
        dialogState.expect(press.user().getId(), step, rule.getId());
        show(press, screens.prompt(question, Screens.rule(rule.getId(), "show")));
    }

    private void save(Access.Press press, ResponseRule rule) {
        responseRuleRepository.save(rule);
        show(press, screens.rule(rule));
    }

    private void show(Access.Press press, Screen screen) {
        replies.answerCallback(press.queryId(), null);
        replies.editMenu(press.chatId(), press.messageId(), screen);
    }

    private static Optional<Long> parseId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
