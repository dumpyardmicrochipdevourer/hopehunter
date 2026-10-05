package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.telegram.view.Html;
import com.antonk404.hhbot.telegram.view.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Все отправки бота. Текст везде - HTML из {@link Html}.
 *
 * <p>Если Telegram разметку не принял или текст длиннее его потолка, сообщение уходит ещё раз
 * обычным текстом. Некрасивое сообщение лучше потерянного: карточка вакансии, которая не дошла
 * из-за одного символа в названии, для человека просто не существует.
 */
@Component
public class TelegramReplies {

    private static final Logger logger = LoggerFactory.getLogger(TelegramReplies.class);

    /** Потолок Telegram на текст сообщения. */
    static final int MAX_TEXT = 4096;

    private static final String HTML = "HTML";

    /**
     * Превью выключено везде: в карточке и в списке вакансий есть ссылки на hh, и развёрнутое
     * превью первой из них занимает пол-экрана и сдвигает кнопки.
     */
    private static final LinkPreviewOptions NO_PREVIEW = LinkPreviewOptions.builder().isDisabled(true).build();

    private final TelegramClient telegramClient;

    public TelegramReplies(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void text(Long chatId, String html) {
        send(chatId, html, null);
    }

    /** Новое сообщение с экраном. @return id сообщения или null, если отправить не удалось */
    public Integer menu(Long chatId, Screen screen) {
        return send(chatId, screen.text(), screen.keyboard());
    }

    private Integer send(Long chatId, String html, InlineKeyboardMarkup keyboard) {
        try {
            return telegramClient.execute(message(chatId, html, keyboard, fits(html))).getMessageId();
        } catch (TelegramApiException e) {
            if (!isMarkupError(e)) {
                logger.error("Failed to send message to chat {}", chatId, e);
                return null;
            }
        }
        try {
            return telegramClient.execute(message(chatId, html, keyboard, false)).getMessageId();
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}", chatId, e);
            return null;
        }
    }

    private static SendMessage message(Long chatId, String html, InlineKeyboardMarkup keyboard, boolean markup) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(markup ? html : plain(html))
                .parseMode(markup ? HTML : null)
                .replyMarkup(keyboard)
                .linkPreviewOptions(NO_PREVIEW)
                .build();
    }

    /**
     * Перерисовывает экран в том же сообщении.
     *
     * @return {@code false}, если сообщения больше нет или его нельзя править - тогда вызывающий
     *         шлёт экран новым сообщением
     */
    public boolean editMenu(Long chatId, Integer messageId, Screen screen) {
        return edit(chatId, messageId, screen.text(), screen.keyboard());
    }

    /** Заменяет текст и убирает кнопки, чтобы они не сработали второй раз. */
    public boolean editText(Long chatId, Integer messageId, String html) {
        return edit(chatId, messageId, html, null);
    }

    private boolean edit(Long chatId, Integer messageId, String html, InlineKeyboardMarkup keyboard) {
        try {
            telegramClient.execute(edit(chatId, messageId, html, keyboard, fits(html)));
            return true;
        } catch (TelegramApiException e) {
            // Повторное нажатие той же кнопки даёт «message is not modified» - это не сбой.
            if (mentions(e, "message is not modified")) {
                return true;
            }
            if (!isMarkupError(e)) {
                logger.warn("Failed to edit message {} in chat {}: {}", messageId, chatId, e.getMessage());
                return false;
            }
        }
        try {
            telegramClient.execute(edit(chatId, messageId, html, keyboard, false));
            return true;
        } catch (TelegramApiException e) {
            logger.warn("Failed to edit message {} in chat {}: {}", messageId, chatId, e.getMessage());
            return false;
        }
    }

    private static EditMessageText edit(
            Long chatId, Integer messageId, String html, InlineKeyboardMarkup keyboard, boolean markup) {
        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(markup ? html : plain(html))
                .parseMode(markup ? HTML : null)
                .replyMarkup(keyboard)
                .linkPreviewOptions(NO_PREVIEW)
                .build();
    }

    /** Подтверждает нажатие; без этого клиент несколько секунд крутит часики на кнопке. */
    public void answerCallback(String callbackQueryId, String text) {
        answer(callbackQueryId, text, false);
    }

    /** То же, но всплывающим окном с кнопкой OK - для отказов, которые нельзя пропустить. */
    public void alert(String callbackQueryId, String text) {
        answer(callbackQueryId, text, true);
    }

    private void answer(String callbackQueryId, String text, boolean alert) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(alert)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to answer callback query {}", callbackQueryId, e);
        }
    }

    /** @return удалось ли удалить; сообщение старше двух суток бот удалить уже не может */
    public boolean deleteMessage(Long chatId, Integer messageId) {
        try {
            telegramClient.execute(DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build());
            return true;
        } catch (TelegramApiException e) {
            logger.debug("Failed to delete message {} in {}: {}", messageId, chatId, e.getMessage());
            return false;
        }
    }

    private static boolean fits(String html) {
        return html.length() <= MAX_TEXT;
    }

    private static boolean isMarkupError(TelegramApiException e) {
        return mentions(e, "can't parse entities");
    }

    private static boolean mentions(TelegramApiException e, String fragment) {
        return e.getMessage() != null && e.getMessage().contains(fragment);
    }

    /** Текст без разметки, обрезанный под потолок Telegram. */
    static String plain(String html) {
        return Html.clip(Html.plain(html), MAX_TEXT);
    }
}
