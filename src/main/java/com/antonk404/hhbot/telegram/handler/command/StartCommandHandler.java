package com.antonk404.hhbot.telegram.handler.command;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.service.WhitelistService;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.access.AdminAccess;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.view.Screens;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;

/** /start, /menu и /cancel - все три ведут в главное меню и обрывают незаконченный ввод. */
@Component
public class StartCommandHandler implements CommandHandler {

    private final Access access;
    private final AdminAccess adminAccess;
    private final WhitelistService whitelistService;
    private final DialogState dialogState;
    private final Screens screens;
    private final TelegramReplies replies;

    public StartCommandHandler(
            Access access,
            AdminAccess adminAccess,
            WhitelistService whitelistService,
            DialogState dialogState,
            Screens screens,
            TelegramReplies replies) {
        this.access = access;
        this.adminAccess = adminAccess;
        this.whitelistService = whitelistService;
        this.dialogState = dialogState;
        this.screens = screens;
        this.replies = replies;
    }

    @Override
    public boolean supports(String text) {
        return Commands.isCommand(text, "/start") || Commands.isCommand(text, "/menu")
                || Commands.isCommand(text, "/cancel");
    }

    @Override
    public void handle(Message message) {
        String username = message.getFrom().getUserName();
        // Админ из конфига попадает в вайтлист сам: иначе первому человеку некому выдать доступ.
        if (adminAccess.isAdmin(username)) {
            whitelistService.add(username, username);
        }
        Optional<BotUser> user = access.user(message);
        if (user.isEmpty()) {
            replies.text(message.getChatId(), "Нет доступа. Попроси администратора добавить тебя.");
            return;
        }
        dialogState.clear(user.get().getId());
        replies.menu(message.getChatId(), screens.home(user.get()));
    }
}
