package com.antonk404.hhbot.hh;

/** hh ответил не тем, что бот умеет разобрать, или не ответил вовсе. */
public class HhException extends RuntimeException {

    public HhException(String message) {
        super(message);
    }

    public HhException(String message, Throwable cause) {
        super(message, cause);
    }
}
