package com.antonk404.hhbot.config;

import com.antonk404.hhbot.telegram.handler.command.MenuCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * Список команд для кнопки «Меню» рядом с полем ввода.
 *
 * <p>Ставится при каждом старте, а не один раз руками через BotFather: так список не может
 * отстать от кода. Админские команды сюда не входят - список видят все.
 */
@Component
public class BotCommands {

    private static final Logger logger = LoggerFactory.getLogger(BotCommands.class);

    private final TelegramClient telegramClient;

    public BotCommands(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .commands(List.of(
                            command(MenuCommandHandler.MENU, "Главное меню"),
                            command(MenuCommandHandler.RULES, "Правила отклика"),
                            command(MenuCommandHandler.STATS, "Статистика откликов"),
                            command(MenuCommandHandler.PAUSE, "Пауза: поставить или снять"),
                            command(MenuCommandHandler.CANCEL, "Отменить ввод")))
                    .build());
        } catch (TelegramApiException e) {
            // Без списка команд бот работает как раньше - это не повод не стартовать.
            logger.warn("Failed to register bot commands: {}", e.getMessage());
        }
    }

    private static BotCommand command(String name, String description) {
        return BotCommand.builder().command(name.substring(1)).description(description).build();
    }
}
