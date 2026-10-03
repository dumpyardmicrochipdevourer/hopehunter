package com.antonk404.hhbot.telegram.handler.command;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.service.WhitelistService;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.AdminAccess;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.stream.Collectors;

/** /whitelist_add, /whitelist_remove, /whitelist_list - только для админов из конфига. */
@Component
public class WhitelistCommandHandler implements CommandHandler {

    private static final String ADD = "/whitelist_add";
    private static final String REMOVE = "/whitelist_remove";
    private static final String LIST = "/whitelist_list";

    private final WhitelistService whitelistService;
    private final AdminAccess adminAccess;
    private final TelegramReplies replies;

    public WhitelistCommandHandler(WhitelistService whitelistService, AdminAccess adminAccess, TelegramReplies replies) {
        this.whitelistService = whitelistService;
        this.adminAccess = adminAccess;
        this.replies = replies;
    }

    @Override
    public boolean supports(String text) {
        return Commands.isCommand(text, ADD) || Commands.isCommand(text, REMOVE) || Commands.isCommand(text, LIST);
    }

    @Override
    public void handle(Message message) {
        Long chatId = message.getChatId();
        String admin = message.getFrom().getUserName();
        if (!adminAccess.isAdmin(admin)) {
            replies.text(chatId, "Только для администраторов.");
            return;
        }

        String[] tokens = Commands.tokens(message.getText());
        String command = tokens[0].toLowerCase();
        if (command.equals(LIST)) {
            String list = whitelistService.listAll().stream()
                    .map(BotUser::getUsername)
                    .map(username -> "@" + username)
                    .collect(Collectors.joining("\n"));
            replies.text(chatId, list.isEmpty() ? "Вайтлист пуст." : list);
            return;
        }
        if (tokens.length != 2) {
            replies.text(chatId, "Формат: " + command + " <username>");
            return;
        }

        String username = WhitelistService.normalize(tokens[1]);
        if (command.equals(ADD)) {
            whitelistService.add(username, admin);
            replies.text(chatId, "Добавил @" + username + ".");
        } else if (whitelistService.remove(username)) {
            replies.text(chatId, "Убрал @" + username + " вместе с его сессией hh, правилами и письмами.");
        } else {
            replies.text(chatId, "@" + username + " в вайтлисте нет.");
        }
    }
}
