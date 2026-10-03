package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.service.WhitelistService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;

/** Кто пишет боту и можно ли ему. Один вход для сообщений и для нажатий. */
@Component
public class Access {

    private final WhitelistService whitelistService;
    private final TelegramReplies replies;

    public Access(WhitelistService whitelistService, TelegramReplies replies) {
        this.whitelistService = whitelistService;
        this.replies = replies;
    }

    public Optional<BotUser> user(Message message) {
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
        if (message == null) {
            replies.answerCallback(query.getId(), "сообщение устарело, открой /menu");
            return Optional.empty();
        }
        Optional<BotUser> user = resolve(query.getFrom(), message.getChatId());
        if (user.isEmpty()) {
            replies.answerCallback(query.getId(), "нет доступа");
            return Optional.empty();
        }
        return Optional.of(new Press(
                user.get(), message.getChatId(), message.getMessageId(), query.getId(), query.getData()));
    }

    private Optional<BotUser> resolve(User from, Long chatId) {
        if (from == null) {
            return Optional.empty();
        }
        Optional<BotUser> user = whitelistService.resolve(from.getId(), from.getUserName());
        if (chatId != null) {
            user.ifPresent(found -> whitelistService.bind(found, from.getId(), chatId));
        }
        return user;
    }
}
