package com.antonk404.hhbot.telegram.handler.command;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.service.UserService;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.access.BotContact;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.MenuMessage;
import com.antonk404.hhbot.telegram.view.MenuScreens;
import com.antonk404.hhbot.telegram.view.RuleScreens;
import com.antonk404.hhbot.telegram.view.Screen;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

/**
 * Команды из кнопки «Меню» Telegram. Каждая открывает экран и обрывает незаконченный ввод -
 * поэтому /cancel здесь же: это то же самое, что /menu.
 */
@Component
public class MenuCommandHandler implements CommandHandler {

    public static final String MENU = "/menu";
    public static final String RULES = "/rules";
    public static final String STATS = "/stats";
    public static final String PAUSE = "/pause";
    public static final String CANCEL = "/cancel";

    private static final List<String> COMMANDS = List.of("/start", MENU, RULES, STATS, PAUSE, CANCEL);

    private final Access access;
    private final UserService userService;
    private final DialogState dialogState;
    private final MenuMessage menu;
    private final MenuScreens menuScreens;
    private final RuleScreens ruleScreens;
    private final TelegramReplies replies;
    private final BotContact botContact;

    public MenuCommandHandler(
            Access access,
            UserService userService,
            DialogState dialogState,
            MenuMessage menu,
            MenuScreens menuScreens,
            RuleScreens ruleScreens,
            TelegramReplies replies,
            BotContact botContact) {
        this.botContact = botContact;
        this.access = access;
        this.userService = userService;
        this.dialogState = dialogState;
        this.menu = menu;
        this.menuScreens = menuScreens;
        this.ruleScreens = ruleScreens;
        this.replies = replies;
    }

    @Override
    public boolean supports(String text) {
        return COMMANDS.stream().anyMatch(command -> Commands.isCommand(text, command));
    }

    @Override
    public void handle(Message message) {
        Optional<BotUser> found = access.user(message);
        if (found.isEmpty()) {
            replies.text(message.getChatId(), botContact.denied());
            return;
        }
        BotUser user = found.get();
        dialogState.clear(user.getId());

        String command = Commands.tokens(message.getText())[0].toLowerCase();
        Screen screen = switch (command) {
            case RULES -> ruleScreens.list(user);
            case STATS -> menuScreens.stats(user);
            case PAUSE -> {
                userService.setPaused(user, !user.isPaused());
                yield menuScreens.home(user);
            }
            default -> menuScreens.home(user);
        };
        menu.replace(user, message.getChatId(), screen);
    }
}
