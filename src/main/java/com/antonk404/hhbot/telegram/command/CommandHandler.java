package com.antonk404.hhbot.telegram.command;

import org.telegram.telegrambots.meta.api.objects.message.Message;

public interface CommandHandler {

    boolean supports(String text);

    void handle(Message message);
}
