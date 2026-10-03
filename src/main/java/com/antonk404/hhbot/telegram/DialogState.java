package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.store.KeyValueStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Чего бот сейчас ждёт от человека текстом: ключевые слова, куки, тело письма.
 *
 * <p>В redis с коротким ttl, а не в базе. Незаконченный ввод ничего не стоит потерять, а вот
 * вечное ожидание опасно: слово, написанное в чат через неделю, молча стало бы названием правила.
 */
@Component
public class DialogState {

    public enum Step {
        ACCOUNT_COOKIES,
        RULE_NEW,
        RULE_NAME,
        RULE_KEYWORDS,
        RULE_MINUS,
        RULE_BLACKLIST,
        RULE_SALARY,
        RULE_AREA,
        RULE_LIMIT,
        LETTER_NEW_NAME,
        LETTER_NEW_BODY,
        LETTER_BODY
    }

    /** @param arg id правила или письма, либо имя нового письма; пустая строка, если шагу нечего помнить */
    public record Dialog(Step step, String arg) {

        public Long id() {
            return Long.valueOf(arg);
        }
    }

    private static final Duration TTL = Duration.ofMinutes(15);

    private final KeyValueStore store;

    public DialogState(KeyValueStore store) {
        this.store = store;
    }

    public void expect(Long userId, Step step, Object arg) {
        store.set(key(userId), step.name() + "\n" + arg, TTL);
    }

    public void expect(Long userId, Step step) {
        expect(userId, step, "");
    }

    public Optional<Dialog> current(Long userId) {
        return store.get(key(userId)).map(value -> {
            String[] parts = value.split("\n", 2);
            return new Dialog(Step.valueOf(parts[0]), parts.length < 2 ? "" : parts[1]);
        });
    }

    public void clear(Long userId) {
        store.delete(key(userId));
    }

    private static String key(Long userId) {
        return "dlg:" + userId;
    }
}
