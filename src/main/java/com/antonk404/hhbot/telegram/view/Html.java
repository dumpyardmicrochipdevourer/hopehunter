package com.antonk404.hhbot.telegram.view;

/**
 * Разметка сообщений. Telegram в режиме HTML отвергает сообщение целиком, если в тексте
 * встретился неэкранированный {@code <} или {@code &}, а названия вакансий и письма пишут люди -
 * поэтому всё пользовательское проходит через {@link #esc}, без исключений.
 */
public final class Html {

    private Html() {
    }

    public static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String b(String value) {
        return "<b>" + esc(value) + "</b>";
    }

    /** Моноширинный текст: в Telegram копируется одним тапом. */
    public static String code(String value) {
        return "<code>" + esc(value) + "</code>";
    }

    public static String quote(String value) {
        return "<blockquote>" + esc(value) + "</blockquote>";
    }

    public static String link(String text, String url) {
        return "<a href=\"" + esc(url).replace("\"", "&quot;") + "\">" + esc(text) + "</a>";
    }

    /** Обратное преобразование - для запасной отправки без разметки. */
    public static String plain(String html) {
        return html.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    /** Обрезает обычный текст до того, как он попадёт в разметку: резать готовый HTML нельзя, порвётся тег. */
    public static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
