package com.antonk404.hhbot.telegram.view;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Сборка инлайн-клавиатур без пяти строк билдера на каждую кнопку. */
public final class Kb {

    /** Потолок Telegram на callback data. Превышение роняет отправку всего сообщения. */
    static final int MAX_CALLBACK_BYTES = 64;

    /** Длиннее Telegram обрезает подпись сам, причём посередине слова и без многоточия. */
    private static final int MAX_VALUE = 22;

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

    /**
     * Кнопка-поле: «Регион: Москва». Значение видно сразу, без захода внутрь - иначе экран
     * настроек превращается в список слов, за каждым из которых надо сходить.
     */
    public static InlineKeyboardButton field(String name, String value, String data) {
        return btn(name + ": " + Html.clip(value, MAX_VALUE), data);
    }

    /** Кнопка, открывающая мини-приложение внутри Telegram. Адрес обязан быть https. */
    public static InlineKeyboardButton webApp(String text, String url) {
        return InlineKeyboardButton.builder().text(text).webApp(new WebAppInfo(url)).build();
    }

    public static InlineKeyboardButton link(String text, String url) {
        return InlineKeyboardButton.builder().text(text).url(url).build();
    }
}
