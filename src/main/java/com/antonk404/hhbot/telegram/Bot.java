package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.telegram.handler.callback.CallbackHandler;
import com.antonk404.hhbot.telegram.handler.command.CommandHandler;
import com.antonk404.hhbot.telegram.handler.dialog.DialogHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

@Component
public class Bot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private final List<CommandHandler> commandHandlers;
    private final List<CallbackHandler> callbackHandlers;
    private final DialogHandler dialogHandler;

    public Bot(
            List<CommandHandler> commandHandlers,
            List<CallbackHandler> callbackHandlers,
            DialogHandler dialogHandler) {
        this.commandHandlers = commandHandlers;
        this.callbackHandlers = callbackHandlers;
        this.dialogHandler = dialogHandler;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                consumeCallback(update.getCallbackQuery());
                return;
            }
            if (!update.hasMessage()) {
                return;
            }
            Message message = update.getMessage();
            // Только личка: в группе меню с чужими кнопками и куки в общем чате - не то, что нужно.
            if (!message.isUserMessage()) {
                return;
            }
            String text = message.getText();
            if (text == null || text.isBlank()) {
                return;
            }
            // Команды первыми: /cancel и /menu должны работать и посреди ввода, иначе из
            // зависшего диалога не выйти.
            Optional<CommandHandler> command = commandHandlers.stream()
                    .filter(handler -> handler.supports(text))
                    .findFirst();
            if (command.isPresent()) {
                command.get().handle(message);
            } else {
                dialogHandler.handle(message);
            }
        } catch (RuntimeException e) {
            // Без типа сообщения и без текста: текстом может оказаться кука.
            logger.error("update {} failed", update.getUpdateId(), e);
        }
    }

    private void consumeCallback(CallbackQuery query) {
        String data = query.getData();
        if (data == null || data.isBlank()) {
            return;
        }
        callbackHandlers.stream()
                .filter(handler -> handler.supports(data))
                .findFirst()
                .ifPresent(handler -> handler.handle(query));
    }
}
