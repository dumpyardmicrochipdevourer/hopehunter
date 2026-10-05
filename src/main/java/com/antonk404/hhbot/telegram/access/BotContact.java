package com.antonk404.hhbot.telegram.access;

import com.antonk404.hhbot.service.WhitelistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Что видит человек не из вайтлиста. Фраза одна на чат, кнопки и мини-приложение: иначе тег того,
 * к кому идти за доступом, пришлось бы менять в трёх местах.
 */
@Component
public class BotContact {

    private final String denied;

    public BotContact(@Value("${bot.contact-username}") String contactUsername) {
        this.denied = "whitelist only. dm @" + WhitelistService.normalize(contactUsername);
    }

    public String denied() {
        return denied;
    }
}
