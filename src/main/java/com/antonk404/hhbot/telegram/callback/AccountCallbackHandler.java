package com.antonk404.hhbot.telegram.callback;

import com.antonk404.hhbot.hh.HhException;
import com.antonk404.hhbot.hh.HhSessionExpiredException;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.telegram.Access;
import com.antonk404.hhbot.telegram.DialogState;
import com.antonk404.hhbot.telegram.Screens;
import com.antonk404.hhbot.telegram.TelegramReplies;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/** Раздел «Аккаунт hh»: подключение сессии, список резюме, отключение. */
@Component
public class AccountCallbackHandler implements CallbackHandler {

    public static final String COOKIES = "acc:cookies";

    private final Access access;
    private final HhAccountService hhAccountService;
    private final DialogState dialogState;
    private final Screens screens;
    private final TelegramReplies replies;

    public AccountCallbackHandler(
            Access access,
            HhAccountService hhAccountService,
            DialogState dialogState,
            Screens screens,
            TelegramReplies replies) {
        this.access = access;
        this.hhAccountService = hhAccountService;
        this.dialogState = dialogState;
        this.screens = screens;
        this.replies = replies;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith("acc:");
    }

    @Override
    public void handle(CallbackQuery query) {
        access.press(query).ifPresent(this::handle);
    }

    private void handle(Access.Press press) {
        Long userId = press.user().getId();
        dialogState.clear(userId);
        switch (press.data()) {
            case COOKIES -> {
                dialogState.expect(userId, DialogState.Step.ACCOUNT_COOKIES);
                replies.answerCallback(press.queryId(), null);
                replies.editMenu(press.chatId(), press.messageId(), screens.cookiesPrompt());
            }
            case "acc:logout" -> {
                hhAccountService.disconnect(userId);
                replies.answerCallback(press.queryId(), "отключил");
                replies.editMenu(press.chatId(), press.messageId(), screens.account(press.user()));
            }
            case "acc:resumes" -> {
                try {
                    var profile = hhAccountService.profile(userId);
                    replies.answerCallback(press.queryId(), null);
                    replies.editMenu(press.chatId(), press.messageId(), screens.resumes(profile.resumes()));
                } catch (HhSessionExpiredException e) {
                    replies.answerCallback(press.queryId(), "сессия истекла");
                    replies.editMenu(press.chatId(), press.messageId(), screens.account(press.user()));
                } catch (HhException e) {
                    replies.alert(press.queryId(), "hh не ответил, попробуй позже");
                }
            }
            default -> {
                replies.answerCallback(press.queryId(), null);
                replies.editMenu(press.chatId(), press.messageId(), screens.account(press.user()));
            }
        }
    }
}
