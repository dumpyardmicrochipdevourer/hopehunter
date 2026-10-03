package com.antonk404.hhbot.telegram.handler.dialog;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.util.LetterRenderer;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.DialogState.Dialog;
import com.antonk404.hhbot.telegram.state.DialogState.Step;
import com.antonk404.hhbot.telegram.state.MenuMessage;
import com.antonk404.hhbot.telegram.view.AccountScreens;
import com.antonk404.hhbot.telegram.view.Cb;
import com.antonk404.hhbot.telegram.view.Html;
import com.antonk404.hhbot.telegram.view.LetterScreens;
import com.antonk404.hhbot.telegram.view.MenuScreens;
import com.antonk404.hhbot.telegram.view.RuleScreens;
import com.antonk404.hhbot.telegram.view.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

/**
 * Обычный текст в чате: ответ на вопрос, который бот задал предыдущим экраном.
 *
 * <p>Какой именно вопрос - лежит в {@link DialogState}. Сообщение человека после разбора удаляется,
 * а меню приходит заново вниз чата: в переписке остаётся одно актуальное меню, а не лента из
 * вопросов, ответов и копий экрана.
 */
@Component
public class DialogHandler {

    private static final Logger logger = LoggerFactory.getLogger(DialogHandler.class);

    private static final int MAX_NAME = 64;
    private static final int MAX_LIST = 500;

    private final Access access;
    private final DialogState dialogState;
    private final HhAccountService hhAccountService;
    private final ResponseRuleRepository responseRuleRepository;
    private final LetterTemplateRepository letterTemplateRepository;
    private final MenuMessage menu;
    private final MenuScreens menuScreens;
    private final AccountScreens accountScreens;
    private final RuleScreens ruleScreens;
    private final LetterScreens letterScreens;
    private final TelegramReplies replies;

    public DialogHandler(
            Access access,
            DialogState dialogState,
            HhAccountService hhAccountService,
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository,
            MenuMessage menu,
            MenuScreens menuScreens,
            AccountScreens accountScreens,
            RuleScreens ruleScreens,
            LetterScreens letterScreens,
            TelegramReplies replies) {
        this.access = access;
        this.dialogState = dialogState;
        this.hhAccountService = hhAccountService;
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
        this.menu = menu;
        this.menuScreens = menuScreens;
        this.accountScreens = accountScreens;
        this.ruleScreens = ruleScreens;
        this.letterScreens = letterScreens;
        this.replies = replies;
    }

    public void handle(Message message) {
        Optional<BotUser> found = access.user(message);
        if (found.isEmpty()) {
            return;
        }
        BotUser user = found.get();
        Long chatId = message.getChatId();
        Optional<Dialog> dialog = dialogState.current(user.getId());
        if (dialog.isEmpty()) {
            // Бот ничего не спрашивал. Вместо «не понял» - меню: человек, скорее всего, его и искал.
            menu.replace(user, chatId, menuScreens.home(user));
            return;
        }
        String text = message.getText().strip();
        // Удаляется при любом исходе. Для куки это обязательно - ключу от аккаунта не место в
        // истории чата; для остального просто не копит мусор.
        boolean deleted = replies.deleteMessage(chatId, message.getMessageId());

        Screen next = switch (dialog.get().step()) {
            case ACCOUNT_COOKIES -> connect(user, chatId, message.getText(), deleted);
            case RULE_NEW -> newRule(user, text);
            case RULE_WIZARD_KEYWORDS -> wizardKeywords(user, chatId, dialog.get(), text);
            case LETTER_NEW_NAME -> newLetterName(user, text);
            case LETTER_NEW_BODY -> newLetterBody(user, dialog.get(), text);
            case LETTER_BODY -> letterBody(user, dialog.get(), text);
            default -> editRule(user, dialog.get(), text);
        };
        // null - шаг уже сам нарисовал результат поверх своего «жду hh».
        if (next != null) {
            menu.replace(user, chatId, next);
        }
    }

    private Screen connect(BotUser user, Long chatId, String pasted, boolean deleted) {
        String leftover = deleted ? "" : "\n\n⚠️ Удалить твоё сообщение с ключом я не смог - удали его сам.";
        menu.replace(user, chatId, new Screen("⏳ Проверяю на hh…", null));
        Screen result;
        try {
            HhProfile profile = hhAccountService.connect(user.getId(), pasted);
            dialogState.clear(user.getId());
            boolean hasRules = !responseRuleRepository.findByUserIdOrderByIdAsc(user.getId()).isEmpty();
            Screen done = accountScreens.connected(profile, hasRules);
            result = new Screen(done.text() + leftover, done.keyboard());
        } catch (IllegalArgumentException e) {
            result = MenuScreens.retry("Не нашёл здесь значение <code>hhtoken</code>. Нужна строка из таблицы "
                    + "кук с этим именем - длинный набор букв и цифр." + leftover, Cb.ACCOUNT);
        } catch (HhSessionExpiredException e) {
            result = MenuScreens.retry("hh это значение не принял - похоже, оно от уже закрытой сессии. "
                    + "Обнови страницу hh.ru, убедись, что ты вошёл, и скопируй заново." + leftover, Cb.ACCOUNT);
        } catch (HhException e) {
            logger.warn("hh profile check failed for user {}: {}", user.getId(), e.getMessage());
            result = MenuScreens.retry("hh сейчас не отвечает, проверить не получилось. Значение я не сохранил."
                    + leftover, Cb.ACCOUNT);
        }
        menu.update(user, chatId, result);
        return null;
    }

    private Screen newRule(BotUser user, String text) {
        if (text.length() > MAX_NAME) {
            return MenuScreens.retry(tooLong(text, MAX_NAME), Cb.RULES);
        }
        ResponseRule rule = responseRuleRepository.save(new ResponseRule(user.getId(), text));
        dialogState.expect(user.getId(), Step.RULE_WIZARD_KEYWORDS, rule.getId());
        return MenuScreens.prompt("""
                <b>Новое правило · шаг 2 из 4</b>

                Что искать? Напиши так, как искал бы на hh.

                Например: <code>java разработчик</code>

                Искать буду в названиях вакансий. Регион, зарплату и остальное можно добавить потом.""",
                Cb.rule(rule.getId(), "show"));
    }

    /** После слов мастер сам идёт за резюме в hh; если не вышло - шаг пропускается, а не мастер обрывается. */
    private Screen wizardKeywords(BotUser user, Long chatId, Dialog dialog, String text) {
        Optional<ResponseRule> found = responseRuleRepository.findByIdAndUserId(dialog.id(), user.getId());
        if (found.isEmpty()) {
            dialogState.clear(user.getId());
            return ruleScreens.list(user);
        }
        ResponseRule rule = found.get();
        if (text.length() > MAX_LIST) {
            return MenuScreens.retry(tooLong(text, MAX_LIST), Cb.rule(rule.getId(), "show"));
        }
        rule.setKeywords(text);
        responseRuleRepository.save(rule);
        dialogState.clear(user.getId());

        menu.replace(user, chatId, new Screen("⏳ Спрашиваю у hh твои резюме…", null));
        Screen next;
        try {
            List<HhResume> resumes = hhAccountService.profile(user.getId()).resumes();
            next = ruleScreens.resumes(rule, resumes, true);
        } catch (HhException e) {
            next = ruleScreens.wizardMode(rule);
        }
        menu.update(user, chatId, next);
        return null;
    }

    private Screen newLetterName(BotUser user, String text) {
        if (text.length() > MAX_NAME) {
            return MenuScreens.retry(tooLong(text, MAX_NAME), Cb.LETTERS);
        }
        dialogState.expect(user.getId(), Step.LETTER_NEW_BODY, text);
        return MenuScreens.prompt("<b>Новое письмо · шаг 2 из 2</b>\n\nТеперь пришли текст письма «"
                + Html.esc(text) + "».\n\n" + LetterScreens.aliasHelp(), Cb.LETTERS);
    }

    private Screen newLetterBody(BotUser user, Dialog dialog, String text) {
        Optional<String> problem = letterProblem(text);
        if (problem.isPresent()) {
            return MenuScreens.retry(problem.get(), Cb.LETTERS);
        }
        LetterTemplate template = letterTemplateRepository.save(new LetterTemplate(user.getId(), dialog.arg(), text));
        dialogState.clear(user.getId());
        return letterScreens.letter(template);
    }

    private Screen letterBody(BotUser user, Dialog dialog, String text) {
        Optional<LetterTemplate> template = letterTemplateRepository.findByIdAndUserId(dialog.id(), user.getId());
        if (template.isEmpty()) {
            dialogState.clear(user.getId());
            return letterScreens.list(user);
        }
        Optional<String> problem = letterProblem(text);
        if (problem.isPresent()) {
            return MenuScreens.retry(problem.get(), Cb.letter(template.get().getId(), "show"));
        }
        template.get().setBody(text);
        letterTemplateRepository.save(template.get());
        dialogState.clear(user.getId());
        return letterScreens.letter(template.get());
    }

    private Screen editRule(BotUser user, Dialog dialog, String text) {
        Optional<ResponseRule> found = responseRuleRepository.findByIdAndUserId(dialog.id(), user.getId());
        if (found.isEmpty()) {
            dialogState.clear(user.getId());
            return ruleScreens.list(user);
        }
        ResponseRule rule = found.get();
        Long id = rule.getId();
        boolean clear = text.equals("-");
        // Куда вернуться: в ту группу настроек, из которой человек пришёл.
        String group = switch (dialog.step()) {
            case RULE_NAME -> "show";
            case RULE_LIMIT -> RuleScreens.HOW;
            default -> RuleScreens.SEARCH;
        };
        String back = Cb.rule(id, group);

        switch (dialog.step()) {
            case RULE_NAME -> {
                if (text.length() > MAX_NAME) {
                    return MenuScreens.retry(tooLong(text, MAX_NAME), back);
                }
                rule.setName(text);
            }
            case RULE_KEYWORDS -> {
                if (text.length() > MAX_LIST) {
                    return MenuScreens.retry(tooLong(text, MAX_LIST), back);
                }
                rule.setKeywords(text);
            }
            case RULE_MINUS -> {
                if (text.length() > MAX_LIST) {
                    return MenuScreens.retry(tooLong(text, MAX_LIST), back);
                }
                rule.setMinusWords(clear ? null : text);
            }
            case RULE_BLACKLIST -> {
                if (text.length() > MAX_LIST) {
                    return MenuScreens.retry(tooLong(text, MAX_LIST), back);
                }
                rule.setCompanyBlacklist(clear ? null : text);
            }
            case RULE_SALARY -> {
                Optional<Integer> salary = number(text, 1, 100_000_000);
                if (salary.isEmpty()) {
                    return MenuScreens.retry("Нужно число в рублях, например <code>180000</code>.", back);
                }
                rule.setSalaryFrom(salary.get());
            }
            case RULE_AREA -> {
                Optional<Integer> area = number(text, 1, 1_000_000);
                if (area.isEmpty()) {
                    return MenuScreens.retry("Нужен номер региона числом, например <code>88</code>.", back);
                }
                rule.setAreaId(area.get());
            }
            case RULE_LIMIT -> {
                Optional<Integer> limit = number(text, 1, 100);
                if (limit.isEmpty()) {
                    return MenuScreens.retry("Нужно число от 1 до 100.", back);
                }
                rule.setDailyLimit(limit.get());
            }
            default -> throw new IllegalStateException("not a rule step: " + dialog.step());
        }
        responseRuleRepository.save(rule);
        dialogState.clear(user.getId());
        return switch (group) {
            case RuleScreens.SEARCH -> ruleScreens.search(rule);
            case RuleScreens.HOW -> ruleScreens.how(rule);
            default -> ruleScreens.rule(rule);
        };
    }

    /** Что не так с текстом письма; пусто - всё в порядке. */
    private static Optional<String> letterProblem(String text) {
        if (text.length() > LetterTemplate.MAX_BODY) {
            return Optional.of(tooLong(text, LetterTemplate.MAX_BODY));
        }
        List<String> unknown = LetterRenderer.unknownAliases(text);
        if (!unknown.isEmpty()) {
            return Optional.of("Не знаю, что подставить вместо " + Html.code("[" + String.join("], [", unknown) + "]")
                    + " - работодатель увидел бы это как есть.\n\nЯ умею: " + Html.esc(LetterScreens.aliasList()) + ".");
        }
        return Optional.empty();
    }

    private static String tooLong(String text, int max) {
        return "Слишком длинно: " + text.length() + " символов, а помещается " + max + ".";
    }

    static Optional<Integer> number(String text, int min, int max) {
        String digits = text.replaceAll("[\\s_ ]", "");
        if (!digits.matches("\\d{1,9}")) {
            return Optional.empty();
        }
        int value = Integer.parseInt(digits);
        return value < min || value > max ? Optional.empty() : Optional.of(value);
    }
}
