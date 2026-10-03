package com.antonk404.hhbot.telegram.view;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Сборка инлайн-клавиатур без пяти строк билдера на каждую кнопку. */
public final class Kb {

    /** Потолок Telegram на callback data. Превышение роняет отправку всего сообщения. */
    static final int MAX_CALLBACK_BYTES = 64;

    private final List<InlineKeyboardRow> rows = new ArrayList<>();

    private Kb() {
    }

    public static Kb of() {
        return new Kb();
    }

    public Kb row(InlineKeyboardButton... buttons) {
        rows.add(new InlineKeyboardRow(buttons));
        return this;
    }

    public InlineKeyboardMarkup build() {
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardButton btn(String text, String data) {
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BYTES) {
            // Громко и на этапе сборки экрана: иначе это «400 BUTTON_DATA_INVALID» где-то в логе
            // и молчащая кнопка у человека.
            throw new IllegalArgumentException("callback data over 64 bytes: " + data);
        }
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    public static InlineKeyboardButton link(String text, String url) {
        return InlineKeyboardButton.builder().text(text).url(url).build();
    }
}
