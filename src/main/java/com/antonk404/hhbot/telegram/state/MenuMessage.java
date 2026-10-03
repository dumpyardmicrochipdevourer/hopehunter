package com.antonk404.hhbot.telegram.state;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.store.KeyValueStore;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.view.Screen;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Следит, чтобы меню в чате было одно.
 *
 * <p>Нажатие кнопки перерисовывает меню на месте. Текстовый ввод устроен иначе: к этому моменту
 * меню уже уехало вверх - под ним вопрос бота, ответ человека, возможно карточки от сканера. Править
 * его там бессмысленно, человек смотрит в низ чата. Поэтому старое меню удаляется, а новое
 * приходит последним сообщением.
 */
@Component
public class MenuMessage {

    /** Дольше меню не живёт: сообщение старше двух суток бот удалить не может, а править - может. */
    private static final Duration TTL = Duration.ofDays(30);

    private final TelegramReplies replies;
    private final KeyValueStore store;

    public MenuMessage(TelegramReplies replies, KeyValueStore store) {
        this.replies = replies;
        this.store = store;
    }

    /** Ответ на нажатие: экран в том же сообщении. */
    public void edit(Access.Press press, Screen screen) {
        edit(press, screen, null);
    }

    /** То же с короткой подписью-тостом вроде «Сохранено». */
    public void edit(Access.Press press, Screen screen, String toast) {
        replies.answerCallback(press.queryId(), toast);
        finish(press, screen);
    }

    /**
     * Показывает «жду hh» вместо меню. Нажатие при этом уже подтверждено, так что результат
     * потом рисуется через {@link #finish}, а не {@link #edit}.
     */
    public void progress(Access.Press press, String html) {
        replies.answerCallback(press.queryId(), null);
        replies.editText(press.chatId(), press.messageId(), "⏳ " + html);
    }

    public void finish(Access.Press press, Screen screen) {
        if (replies.editMenu(press.chatId(), press.messageId(), screen)) {
            remember(press.user().getId(), press.messageId());
        } else {
            send(press.user(), press.chatId(), screen);
        }
    }

    /** Ответ на текст или команду: старое меню убрать, новое прислать вниз чата. */
    public void replace(BotUser user, Long chatId, Screen screen) {
        current(user.getId()).ifPresent(messageId -> replies.deleteMessage(chatId, messageId));
        send(user, chatId, screen);
    }

    /** Обновляет меню, которое только что прислал {@link #replace}; нужно после «жду hh». */
    public void update(BotUser user, Long chatId, Screen screen) {
        Optional<Integer> messageId = current(user.getId());
        if (messageId.isEmpty() || !replies.editMenu(chatId, messageId.get(), screen)) {
            send(user, chatId, screen);
        }
    }

    private void send(BotUser user, Long chatId, Screen screen) {
        Integer messageId = replies.menu(chatId, screen);
        if (messageId != null) {
            remember(user.getId(), messageId);
        }
    }

    private void remember(Long userId, Integer messageId) {
        store.set(key(userId), String.valueOf(messageId), TTL);
    }

    private Optional<Integer> current(Long userId) {
        return store.get(key(userId)).map(Integer::valueOf);
    }

    private static String key(Long userId) {
        return "menu:" + userId;
    }
}
