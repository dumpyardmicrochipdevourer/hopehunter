package com.antonk404.hhbot.service.exceptions;

/** Правила или письма с таким id у этого пользователя нет - чужое выглядит так же, как несуществующее. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
