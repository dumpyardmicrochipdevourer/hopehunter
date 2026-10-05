package com.antonk404.hhbot.service.exceptions;

/**
 * Человек прислал то, что нельзя сохранить. Сообщение написано для него и показывается как есть.
 */
public class ValidationException extends RuntimeException {

    private final String field;

    /** @param field имя поля в запросе, чтобы форма подсветила нужное; null - ошибка не про поле */
    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
