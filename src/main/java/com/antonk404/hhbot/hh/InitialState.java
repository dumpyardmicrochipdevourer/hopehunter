package com.antonk404.hhbot.hh;

import com.antonk404.hhbot.hh.exceptions.HhException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Достаёт из страницы hh состояние, с которым её рисует фронтенд.
 *
 * <p>Сайт кладёт все данные страницы в {@code <template id="HH-Lux-InitialState">} одним JSON,
 * экранированным как HTML. Читать его надёжнее, чем разметку: классы и вёрстка меняются с каждым
 * редизайном, а этот JSON - контракт между их же бэкендом и фронтендом.
 */
final class InitialState {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern TEMPLATE = Pattern.compile(
            "<template[^>]*id=\"HH-Lux-InitialState\"[^>]*>(.*?)</template>", Pattern.DOTALL);

    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos);");

    private InitialState() {
    }

    static JsonNode parse(String html) {
        Matcher matcher = TEMPLATE.matcher(html == null ? "" : html);
        if (!matcher.find()) {
            throw new HhException("hh page has no initial state - layout changed or anti-bot page");
        }
        try {
            return MAPPER.readTree(unescape(matcher.group(1)));
        } catch (JsonProcessingException e) {
            throw new HhException("hh initial state is not json", e);
        }
    }

    /** Один проход: {@code &amp;#34;} должно остаться текстом {@code &#34;}, а не стать кавычкой. */
    static String unescape(String value) {
        return ENTITY.matcher(value).replaceAll(match -> {
            String name = match.group(1);
            String replacement = switch (name) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                default -> Character.toString(name.startsWith("#x") || name.startsWith("#X")
                        ? Integer.parseInt(name.substring(2), 16)
                        : Integer.parseInt(name.substring(1)));
            };
            return Matcher.quoteReplacement(replacement);
        });
    }
}
