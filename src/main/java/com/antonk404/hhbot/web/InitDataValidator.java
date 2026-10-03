package com.antonk404.hhbot.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Проверяет, что запрос пришёл из мини-приложения, открытого в Telegram этим пользователем.
 *
 * <p>Telegram отдаёт приложению строку {@code initData} и подписывает её HMAC-ом от токена бота.
 * Подделать её без токена нельзя, поэтому отдельных паролей и сессий у приложения нет: каждый
 * запрос несёт эту строку, сервер пересчитывает подпись.
 */
@Component
public class InitDataValidator {

    /** @param username может быть null: тег в Telegram необязателен */
    public record TelegramUser(Long id, String username) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final byte[] secret;
    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public InitDataValidator(
            @Value("${bot.token}") String botToken,
            @Value("${webapp.auth-max-age-hours}") long maxAgeHours) {
        this(botToken, Duration.ofHours(maxAgeHours), Clock.systemUTC());
    }

    InitDataValidator(String botToken, Duration maxAge, Clock clock) {
        this.secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken);
        this.maxAge = maxAge;
        this.clock = clock;
    }

    /** Пользователь из подписанной строки; пусто, если подпись не сошлась, устарела или строка битая. */
    public Optional<TelegramUser> validate(String initData) {
        if (initData == null || initData.isBlank()) {
            return Optional.empty();
        }
        // TreeMap: строка для подписи собирается из пар в алфавитном порядке ключей.
        Map<String, String> fields = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                return Optional.empty();
            }
            fields.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        String hash = fields.remove("hash");
        if (hash == null) {
            return Optional.empty();
        }

        StringBuilder check = new StringBuilder();
        fields.forEach((key, value) -> check.append(check.isEmpty() ? "" : "\n").append(key).append('=').append(value));
        byte[] expected = hmac(secret, check.toString());
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(hash);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // Сравнение за постоянное время: обычный equals выдаёт по времени ответа, сколько байт совпало.
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }

        // Без срока годности перехваченная один раз строка работала бы вечно.
        try {
            Instant issued = Instant.ofEpochSecond(Long.parseLong(fields.getOrDefault("auth_date", "")));
            if (issued.plus(maxAge).isBefore(clock.instant())) {
                return Optional.empty();
            }
            JsonNode user = MAPPER.readTree(fields.getOrDefault("user", ""));
            if (!user.hasNonNull("id")) {
                return Optional.empty();
            }
            return Optional.of(new TelegramUser(user.get("id").asLong(), user.path("username").asText(null)));
        } catch (NumberFormatException | JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 missing", e);
        }
    }
}
