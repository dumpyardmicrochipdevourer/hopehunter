package com.antonk404.hhbot.hh;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
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

    /**
     * Разбирает то, что человек вставил в чат: либо строку заголовка Cookie целиком
     * ({@code name=value; name2=value2}), либо одно значение {@code hhtoken}.
     *
     * <p>Если {@code _xsrf} не пришёл, он генерируется. hh сверяет куку с тем же значением в
     * запросе, а не с чем-то на сервере, так что паре достаточно совпадать между собой.
     *
     * @throws IllegalArgumentException если в тексте нет {@code hhtoken}
     */
    public static HhSessionCookies parse(String pasted) {
        String text = pasted == null ? "" : pasted.strip();
        if (text.regionMatches(true, 0, "cookie:", 0, 7)) {
            text = text.substring(7).strip();
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        if (!text.isEmpty() && !text.contains("=") && !text.contains(" ")) {
            cookies.put(TOKEN, text);
        } else {
            for (String part : text.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                // Делим по первому '=': значения бывают в base64 и заканчиваются на '='.
                cookies.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip());
            }
        }
        String token = cookies.get(TOKEN);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("no hhtoken cookie");
        }
        cookies.computeIfAbsent(XSRF, key -> randomXsrf());
        return new HhSessionCookies(cookies);
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
