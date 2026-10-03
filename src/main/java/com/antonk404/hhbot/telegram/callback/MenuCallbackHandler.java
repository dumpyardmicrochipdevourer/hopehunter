package com.antonk404.hhbot.telegram.callback;

import com.antonk404.hhbot.service.WhitelistService;
import com.antonk404.hhbot.telegram.Access;
import com.antonk404.hhbot.telegram.DialogState;
import com.antonk404.hhbot.telegram.Screens;
import com.antonk404.hhbot.telegram.TelegramReplies;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/** Главное меню, статистика и общая пауза. */
@Component
public class MenuCallbackHandler implements CallbackHandler {

    private final Access access;
    private final WhitelistService whitelistService;
    private final DialogState dialogState;
    private final Screens screens;
    private final TelegramReplies replies;

    public MenuCallbackHandler(
            Access access,
            WhitelistService whitelistService,
            DialogState dialogState,
            Screens screens,
            TelegramReplies replies) {
        this.access = access;
        this.whitelistService = whitelistService;
        this.dialogState = dialogState;
        this.screens = screens;
        this.replies = replies;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith("m:");
    }

    @Override
    public void handle(CallbackQuery query) {
        access.press(query).ifPresent(press -> {
            // Любой переход по меню обрывает ввод: человек передумал и ушёл на другой экран.
            dialogState.clear(press.user().getId());
            switch (press.data()) {
                case Screens.STATS -> {
                    replies.answerCallback(press.queryId(), null);
                    replies.editMenu(press.chatId(), press.messageId(), screens.stats(press.user()));
                }
                case Screens.PAUSE -> {
                    whitelistService.setPaused(press.user(), !press.user().isPaused());
                    replies.answerCallback(press.queryId(), press.user().isPaused() ? "пауза" : "работаю");
                    replies.editMenu(press.chatId(), press.messageId(), screens.home(press.user()));
                }
                default -> {
                    replies.answerCallback(press.queryId(), null);
                    replies.editMenu(press.chatId(), press.messageId(), screens.home(press.user()));
                }
            }
        });
    }
}
