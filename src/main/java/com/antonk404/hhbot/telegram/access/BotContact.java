package com.antonk404.hhbot.telegram.access;

import org.springframework.stereotype.Component;

/** Shared message for a closed registration or an invalid Telegram identity. */
@Component
public class BotContact {
    public String denied() { return "Регистрация пока закрыта."; }
}
