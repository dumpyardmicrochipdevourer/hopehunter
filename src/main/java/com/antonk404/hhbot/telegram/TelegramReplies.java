package com.antonk404.hhbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramReplies {

    private static final Logger logger = LoggerFactory.getLogger(TelegramReplies.class);

    /** Потолок Telegram на текст сообщения. */
    static final int MAX_TEXT = 4096;

    /**
     * Превью выключено везде: в карточке и в списке вакансий есть ссылки на hh, и развёрнутое
     * превью первой из них занимает пол-экрана и сдвигает кнопки.
     */
    private static final LinkPreviewOptions NO_PREVIEW = LinkPreviewOptions.builder().isDisabled(true).build();

    private final TelegramClient telegramClient;

    public TelegramReplies(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void text(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(clip(text))
                    .linkPreviewOptions(NO_PREVIEW)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}", chatId, e);
        }
    }

    /** Новое сообщение с экраном меню. */
    public void menu(Long chatId, Screen screen) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(clip(screen.text()))
                    .replyMarkup(screen.keyboard())
                    .linkPreviewOptions(NO_PREVIEW)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to send menu to chat {}", chatId, e);
        }
    }

    /** Перерисовывает экран в том же сообщении: меню не расползается по чату десятком копий. */
    public void editMenu(Long chatId, Integer messageId, Screen screen) {
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(clip(screen.text()))
                    .replyMarkup(screen.keyboard())
                    .linkPreviewOptions(NO_PREVIEW)
                    .build());
        } catch (TelegramApiException e) {
            // Повторное нажатие той же кнопки даёт «message is not modified» - это не сбой.
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                return;
            }
            logger.error("Failed to edit menu {} in chat {}", messageId, chatId, e);
        }
    }

    /** Заменяет текст и убирает кнопки, чтобы они не сработали второй раз. */
    public void editText(Long chatId, Integer messageId, String text) {
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(clip(text))
                    .linkPreviewOptions(NO_PREVIEW)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }

    /** Подтверждает нажатие; без этого клиент несколько секунд крутит часики на кнопке. */
    public void answerCallback(String callbackQueryId, String text) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to answer callback query {}", callbackQueryId, e);
        }
    }

    /** То же, но всплывающим окном с кнопкой OK - для отказов, которые нельзя пропустить. */
    public void alert(String callbackQueryId, String text) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(true)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to answer callback query {}", callbackQueryId, e);
        }
    }

    /** @return удалось ли удалить; в личном чате бот может удалить сообщение человека не всегда */
    public boolean deleteMessage(Long chatId, Integer messageId) {
        try {
            telegramClient.execute(DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build());
            return true;
        } catch (TelegramApiException e) {
            logger.warn("Failed to delete message {} in {}: {}", messageId, chatId, e.getMessage());
            return false;
        }
    }

    static String clip(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 1) + "…";
    }
}
