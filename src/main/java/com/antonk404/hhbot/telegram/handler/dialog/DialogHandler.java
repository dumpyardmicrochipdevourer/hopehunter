package com.antonk404.hhbot.telegram.handler.dialog;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.util.LetterRenderer;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.view.Screens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

/**
 * Обычный текст в чате: ответ на вопрос, который бот задал предыдущим экраном.
 *
 * <p>Какой именно вопрос - лежит в {@link DialogState}. Если бот ничего не спрашивал, текст
 * игнорируется с подсказкой, а не угадывается.
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
    private final Screens screens;
    private final TelegramReplies replies;

    public DialogHandler(
            Access access,
            DialogState dialogState,
            HhAccountService hhAccountService,
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository,
            Screens screens,
            TelegramReplies replies) {
        this.access = access;
        this.dialogState = dialogState;
        this.hhAccountService = hhAccountService;
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
        this.screens = screens;
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
            replies.text(chatId, "Я сейчас ничего не спрашивал. Меню - /menu.");
            return;
        }
        String text = message.getText().strip();

        switch (dialog.get().step()) {
            case ACCOUNT_COOKIES -> connect(user, message);
            case RULE_NEW -> {
                if (tooLong(chatId, text, MAX_NAME)) {
                    return;
                }
                ResponseRule rule = responseRuleRepository.save(new ResponseRule(user.getId(), text));
                dialogState.clear(user.getId());
                replies.menu(chatId, screens.rule(rule));
            }
            case LETTER_NEW_NAME -> {
                if (tooLong(chatId, text, MAX_NAME)) {
                    return;
                }
                dialogState.expect(user.getId(), DialogState.Step.LETTER_NEW_BODY, text);
                replies.menu(chatId, screens.prompt(
                        "Теперь текст письма «" + text + "».\n\n" + Screens.aliasHelp(), Screens.LETTERS));
            }
            case LETTER_NEW_BODY -> {
                if (badLetter(chatId, text)) {
                    return;
                }
                LetterTemplate template = letterTemplateRepository.save(
                        new LetterTemplate(user.getId(), dialog.get().arg(), text));
                dialogState.clear(user.getId());
                replies.menu(chatId, screens.letter(template));
            }
            case LETTER_BODY -> {
                Optional<LetterTemplate> template =
                        letterTemplateRepository.findByIdAndUserId(dialog.get().id(), user.getId());
                if (template.isEmpty()) {
                    dialogState.clear(user.getId());
                    replies.menu(chatId, screens.letters(user));
                    return;
                }
                if (badLetter(chatId, text)) {
                    return;
                }
                template.get().setBody(text);
                letterTemplateRepository.save(template.get());
                dialogState.clear(user.getId());
                replies.menu(chatId, screens.letter(template.get()));
            }
            default -> editRule(user, chatId, dialog.get(), text);
        }
    }

    /**
     * Сообщение с куками удаляется из чата при любом исходе, ещё до похода в hh: это ключ от
     * аккаунта, и висеть в истории переписки ему незачем.
     */
    private void connect(BotUser user, Message message) {
        Long chatId = message.getChatId();
        boolean deleted = replies.deleteMessage(chatId, message.getMessageId());
        String leftover = deleted ? "" : "\n\nУдалить твоё сообщение я не смог - удали его сам.";
        try {
            HhProfile profile = hhAccountService.connect(user.getId(), message.getText());
            dialogState.clear(user.getId());
            replies.text(chatId, "Подключил. Резюме на hh: " + profile.resumes().size() + "." + leftover);
            replies.menu(chatId, screens.account(user));
        } catch (IllegalArgumentException e) {
            replies.text(chatId, "Не вижу здесь куки hhtoken. Пришли её значение или строку Cookie целиком."
                    + leftover);
        } catch (HhSessionExpiredException e) {
            replies.text(chatId, "hh эти куки не принял: сессия уже не действует. Войди на hh.ru заново "
                    + "и пришли свежее значение." + leftover);
        } catch (HhException e) {
            logger.warn("hh profile check failed for user {}: {}", user.getId(), e.getMessage());
            replies.text(chatId, "hh не ответил, проверить куки не удалось. Попробуй ещё раз позже." + leftover);
        }
    }

    private void editRule(BotUser user, Long chatId, Dialog dialog, String text) {
        Optional<ResponseRule> found = responseRuleRepository.findByIdAndUserId(dialog.id(), user.getId());
        if (found.isEmpty()) {
            dialogState.clear(user.getId());
            replies.menu(chatId, screens.rules(user));
            return;
        }
        ResponseRule rule = found.get();
        boolean clear = text.equals("-");

        switch (dialog.step()) {
            case RULE_NAME -> {
                if (tooLong(chatId, text, MAX_NAME)) {
                    return;
                }
                rule.setName(text);
            }
            case RULE_KEYWORDS -> {
                if (tooLong(chatId, text, MAX_LIST)) {
                    return;
                }
                rule.setKeywords(text);
            }
            case RULE_MINUS -> {
                if (tooLong(chatId, text, MAX_LIST)) {
                    return;
                }
                rule.setMinusWords(clear ? null : text);
            }
            case RULE_BLACKLIST -> {
                if (tooLong(chatId, text, MAX_LIST)) {
                    return;
                }
                rule.setCompanyBlacklist(clear ? null : text);
            }
            case RULE_SALARY -> {
                Optional<Integer> salary = clear ? Optional.empty() : number(text, 1, 100_000_000);
                if (!clear && salary.isEmpty()) {
                    replies.text(chatId, "Нужно число в рублях, например 200000. Или «-», чтобы снять порог.");
                    return;
                }
                rule.setSalaryFrom(salary.orElse(null));
            }
            case RULE_AREA -> {
                Optional<Integer> area = number(text, 1, 1_000_000);
                if (area.isEmpty()) {
                    replies.text(chatId, "Нужен id региона числом, например 88.");
                    return;
                }
                rule.setAreaId(area.get());
            }
            case RULE_LIMIT -> {
                Optional<Integer> limit = number(text, 1, 100);
                if (limit.isEmpty()) {
                    replies.text(chatId, "Нужно число от 1 до 100.");
                    return;
                }
                rule.setDailyLimit(limit.get());
            }
            default -> throw new IllegalStateException("not a rule step: " + dialog.step());
        }
        responseRuleRepository.save(rule);
        dialogState.clear(user.getId());
        replies.menu(chatId, screens.rule(rule));
    }

    private boolean badLetter(Long chatId, String text) {
        if (tooLong(chatId, text, LetterTemplate.MAX_BODY)) {
            return true;
        }
        List<String> unknown = LetterRenderer.unknownAliases(text);
        if (!unknown.isEmpty()) {
            replies.text(chatId, "Не знаю алиас [" + String.join("], [", unknown) + "] - он ушёл бы работодателю "
                    + "как есть. Доступны: " + Screens.aliasList() + ".\n\nПоправь и пришли текст ещё раз.");
            return true;
        }
        return false;
    }

    /** Диалог при этом не сбрасывается: человек поправит и пришлёт снова. */
    private boolean tooLong(Long chatId, String text, int max) {
        if (text.length() > max) {
            replies.text(chatId, "Слишком длинно: " + text.length() + " символов при потолке " + max + ".");
            return true;
        }
        return false;
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
