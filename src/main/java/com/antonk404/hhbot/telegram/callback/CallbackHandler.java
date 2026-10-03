package com.antonk404.hhbot.telegram.callback;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/** Пара к {@code CommandHandler} для нажатий инлайн-кнопок. */
public interface CallbackHandler {

    boolean supports(String data);

    void handle(CallbackQuery query);
}
