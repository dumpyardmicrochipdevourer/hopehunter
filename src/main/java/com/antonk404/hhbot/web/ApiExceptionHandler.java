package com.antonk404.hhbot.web;

import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.exceptions.NotFoundException;
import com.antonk404.hhbot.service.exceptions.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Ошибки API в одном виде: машинный код для приложения и готовая фраза для человека.
 * Приложению не нужно сочинять тексты - оно показывает {@code message}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** @param field поле формы, к которому относится ошибка; null - ко всему запросу */
    public record ApiError(String error, String message, String field) {
    }

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> validation(ValidationException e) {
        return error(HttpStatus.BAD_REQUEST, "validation", e.getMessage(), e.getField());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "validation", "Запрос не разобрать.", null);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "not_found", e.getMessage(), null);
    }

    /** Отдельный код: по нему приложение ведёт на экран подключения, а не показывает ошибку. */
    @ExceptionHandler(HhSessionExpiredException.class)
    public ResponseEntity<ApiError> sessionExpired(HhSessionExpiredException e) {
        return error(HttpStatus.CONFLICT, "hh_session_expired",
                "Аккаунт hh не подключён или сессия истекла.", null);
    }

    @ExceptionHandler(HhException.class)
    public ResponseEntity<ApiError> hhDown(HhException e) {
        logger.warn("hh call failed: {}", e.getMessage());
        // 424, а не 502: ответы 502 и 504 обратные прокси и туннели подменяют своей страницей,
        // и приложение вместо этой фразы получало чужой html.
        return error(HttpStatus.FAILED_DEPENDENCY, "hh_unavailable", "hh сейчас не отвечает. Попробуй чуть позже.", null);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message, String field) {
        return ResponseEntity.status(status).body(new ApiError(code, message, field));
    }
}
