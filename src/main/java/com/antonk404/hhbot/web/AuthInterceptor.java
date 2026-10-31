package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.service.UserService;
import com.antonk404.hhbot.telegram.access.BotContact;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Проверяет подпись Telegram и регистрирует пользователя по Telegram ID.
 *
 * <p>Подпись приходит в заголовке {@code Authorization: tma <initData>} - в заголовке, а не в
 * адресе, потому что адреса оседают в логах прокси, а эта строка работает как пропуск.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Под этим именем контроллеры получают пользователя через {@code @RequestAttribute}. */
    public static final String USER = "botUser";

    private static final String SCHEME = "tma ";

    private final InitDataValidator initDataValidator;
    private final UserService userService;
    private final BotContact botContact;

    public AuthInterceptor(
            InitDataValidator initDataValidator, UserService userService, BotContact botContact) {
        this.botContact = botContact;
        this.initDataValidator = initDataValidator;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String header = request.getHeader("Authorization");
        Optional<InitDataValidator.TelegramUser> telegramUser = header != null && header.startsWith(SCHEME)
                ? initDataValidator.validate(header.substring(SCHEME.length()))
                : Optional.empty();
        if (telegramUser.isEmpty()) {
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized",
                    "Открой приложение из Telegram заново.");
        }
        Optional<BotUser> user = userService.resolve(telegramUser.get().id(), telegramUser.get().username());
        if (user.isEmpty()) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", botContact.denied());
        }
        // Mini App can create a profile, but Telegram must receive /start before notifications work.
        request.setAttribute(USER, user.get());
        return true;
    }

    private static boolean reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
