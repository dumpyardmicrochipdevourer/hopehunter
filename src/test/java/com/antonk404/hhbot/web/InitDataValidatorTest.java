package com.antonk404.hhbot.web;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InitDataValidatorTest {

    private static final String TOKEN = "123456:TEST-TOKEN";
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final InitDataValidator validator =
            new InitDataValidator(TOKEN, Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));

    /** Подписывает так, как это делает Telegram, - независимо от проверяемого кода. */
    public static String sign(String token, Instant issued, String userJson) {
        try {
            TreeMap<String, String> fields = new TreeMap<>();
            fields.put("auth_date", String.valueOf(issued.getEpochSecond()));
            fields.put("query_id", "AAF");
            fields.put("user", userJson);
            StringBuilder check = new StringBuilder();
            fields.forEach((key, value) -> check.append(check.isEmpty() ? "" : "\n").append(key).append('=').append(value));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secret = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String hash = HexFormat.of().formatHex(mac.doFinal(check.toString().getBytes(StandardCharsets.UTF_8)));

            StringBuilder out = new StringBuilder();
            fields.forEach((key, value) -> out.append(key).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&'));
            return out + "hash=" + hash;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String USER = "{\"id\":42,\"first_name\":\"Антон\",\"username\":\"anton\"}";

    @Test
    void acceptsDataSignedWithTheBotToken() {
        assertEquals(Optional.of(new InitDataValidator.TelegramUser(42L, "anton")),
                validator.validate(sign(TOKEN, NOW.minusSeconds(60), USER)));
    }

    @Test
    void userWithoutUsernameIsStillAUser() {
        assertEquals(Optional.of(new InitDataValidator.TelegramUser(42L, null)),
                validator.validate(sign(TOKEN, NOW, "{\"id\":42,\"first_name\":\"A\"}")));
    }

    @Test
    void rejectsDataSignedWithAnotherToken() {
        assertEquals(Optional.empty(), validator.validate(sign("999:OTHER", NOW, USER)));
    }

    /** Главная атака: взять свою валидную строку и подставить в неё чужой id. */
    @Test
    void rejectsTamperedUser() {
        String tampered = sign(TOKEN, NOW, USER).replace("%22id%22%3A42", "%22id%22%3A43");

        assertEquals(Optional.empty(), validator.validate(tampered));
    }

    @Test
    void rejectsStaleData() {
        assertEquals(Optional.empty(), validator.validate(sign(TOKEN, NOW.minus(Duration.ofHours(25)), USER)));
    }

    @Test
    void rejectsGarbageWithoutThrowing() {
        assertEquals(Optional.empty(), validator.validate(null));
        assertEquals(Optional.empty(), validator.validate(""));
        assertEquals(Optional.empty(), validator.validate("hello"));
        assertEquals(Optional.empty(), validator.validate("user=%7B%7D&auth_date=1"));
        assertEquals(Optional.empty(), validator.validate("user=%7B%7D&auth_date=1&hash=zz"));
    }
}
