package com.antonk404.hhbot.telegram.access;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.service.UserService;
import com.antonk404.hhbot.telegram.TelegramReplies;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;

/** Кто пишет боту и можно ли ему. Один вход для сообщений и для нажатий. */
@Component
public class Access {

    private final UserService userService;
    private final TelegramReplies replies;
    private final BotContact botContact;

    public Access(UserService userService, TelegramReplies replies, BotContact botContact) {
        this.botContact = botContact;
        this.userService = userService;
        this.replies = replies;
    }

    public Optional<BotUser> user(Message message) {
        if (message.getFrom() == null || !message.getFrom().getId().equals(message.getChatId())) return Optional.empty();
        return resolve(message.getFrom(), message.getChatId());
    }

    /** Нажатие кнопки, уже привязанное к пользователю и сообщению. */
    public record Press(BotUser user, Long chatId, Integer messageId, String queryId, String data) {
    }

    /**
     * Проверяется на каждом нажатии, а не только на команде: кнопки остаются в чате, нажатие
     * приходит отдельным апдейтом, и доступ за это время могли забрать.
     *
     * <p>Пустой результат значит, что человеку уже ответили - обработчику остаётся выйти.
     */
    public Optional<Press> press(CallbackQuery query) {
        MaybeInaccessibleMessage message = query.getMessage();
        if (message == null || query.getFrom() == null || !query.getFrom().getId().equals(message.getChatId())) {
            replies.answerCallback(query.getId(), "сообщение устарело, открой /menu");
            return Optional.empty();
        }
        Optional<BotUser> user = resolve(query.getFrom(), message.getChatId());
        if (user.isEmpty()) {
            replies.answerCallback(query.getId(), botContact.denied());
            return Optional.empty();
        }
        return Optional.of(new Press(
                user.get(), message.getChatId(), message.getMessageId(), query.getId(), query.getData()));
    }

    private Optional<BotUser> resolve(User from, Long chatId) {
        if (from == null) {
            return Optional.empty();
        }
        Optional<BotUser> user = userService.resolve(from.getId(), from.getUserName());
        if (chatId != null) {
            user.ifPresent(found -> userService.bind(found, from.getId(), chatId));
        }
        return user;
    }
}
