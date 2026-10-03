package com.antonk404.hhbot.hh;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Куки одной сессии hh. Этот объект равен доступу к чужому аккаунту, поэтому {@code toString}
 * переопределён: любое случайное попадание в лог или в текст исключения иначе выплюнуло бы токен.
 */
public final class HhSessionCookies {

    /** Кука, по которой hh узнаёт залогиненного пользователя. Без неё остальные бесполезны. */
    public static final String TOKEN = "hhtoken";
    public static final String XSRF = "_xsrf";

    private final Map<String, String> cookies;

    private HhSessionCookies(Map<String, String> cookies) {
        this.cookies = Collections.unmodifiableMap(cookies);
    }

    /** {@code hhtoken}, за ним любые разделители, за ними значение - так выглядит строка, скопированная из таблицы кук. */
    private static final Pattern LABELLED_TOKEN = Pattern.compile("hhtoken[\"'`\\s:=]+([^\\s;\"'`]+)");

    /**
     * Разбирает то, что человек вставил в чат. Принимает всё, что реально копируется из браузера:
     * одно значение {@code hhtoken}, строку заголовка Cookie целиком, строку из таблицы кук
     * (имя, табуляция, значение, домен…), с кавычками и без.
     *
     * <p>Строгость тут ничего не даёт: значение всё равно сразу проверяется на живом hh. А каждое
     * «не тот формат» - это человек, который полез в инструменты разработчика ещё раз.
     *
     * <p>Если {@code _xsrf} не пришёл, он генерируется. hh сверяет куку с тем же значением в
     * запросе, а не с чем-то на сервере, так что паре достаточно совпадать между собой.
     *
     * @throws IllegalArgumentException если значение {@code hhtoken} найти не удалось
     */
    public static HhSessionCookies parse(String pasted) {
        String text = pasted == null ? "" : pasted.strip();
        if (text.regionMatches(true, 0, "cookie:", 0, 7)) {
            text = text.substring(7).strip();
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        if (text.contains(TOKEN + "=")) {
            for (String part : text.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                // Делим по первому '=': значения бывают в base64 и заканчиваются на '='.
                cookies.put(part.substring(0, eq).strip(), unquote(part.substring(eq + 1)));
            }
        } else {
            Matcher labelled = LABELLED_TOKEN.matcher(text);
            String bare = unquote(text);
            if (labelled.find()) {
                cookies.put(TOKEN, labelled.group(1));
            } else if (!bare.isEmpty() && bare.matches("[\\x21-\\x7e]+") && !bare.contains("=")) {
                // Одно слово из печатных ASCII-символов - это само значение. «привет» сюда не пройдёт.
                cookies.put(TOKEN, bare);
            }
        }
        String token = cookies.get(TOKEN);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("no hhtoken cookie");
        }
        cookies.computeIfAbsent(XSRF, key -> randomXsrf());
        return new HhSessionCookies(cookies);
    }

    private static String unquote(String value) {
        return value.strip().replaceAll("^[\"'`]+|[\"'`]+$", "");
    }

    private static String randomXsrf() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Значение для заголовка {@code Cookie}; оно же - форма хранения, {@link #parse} читает его обратно. */
    public String header() {
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    public String xsrf() {
        return cookies.get(XSRF);
    }

    @Override
    public String toString() {
        return "HhSessionCookies[" + String.join(", ", cookies.keySet()) + "]";
    }
}
