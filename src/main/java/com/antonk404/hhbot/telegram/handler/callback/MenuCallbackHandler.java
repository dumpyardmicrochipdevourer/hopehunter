package com.antonk404.hhbot.telegram.handler.callback;

import com.antonk404.hhbot.service.WhitelistService;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.MenuMessage;
import com.antonk404.hhbot.telegram.view.Cb;
import com.antonk404.hhbot.telegram.view.MenuScreens;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/** Главное меню, статистика и общая пауза. */
@Component
public class MenuCallbackHandler implements CallbackHandler {

    private final Access access;
    private final WhitelistService whitelistService;
    private final DialogState dialogState;
    private final MenuMessage menu;
    private final MenuScreens screens;

    public MenuCallbackHandler(
            Access access,
            WhitelistService whitelistService,
            DialogState dialogState,
            MenuMessage menu,
            MenuScreens screens) {
        this.access = access;
        this.whitelistService = whitelistService;
        this.dialogState = dialogState;
        this.menu = menu;
        this.screens = screens;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith("m:");
    }

    @Override
    public void handle(CallbackQuery query) {
        access.press(query).ifPresent(press -> {
            // Любой переход по меню обрывает ввод: человек передумал и ушёл на другой экран.
            dialogState.clear(press.user().getId());
            switch (press.data()) {
                case Cb.STATS -> menu.edit(press, screens.stats(press.user()));
                case Cb.PAUSE -> {
                    whitelistService.setPaused(press.user(), !press.user().isPaused());
                    menu.edit(press, screens.home(press.user()),
                            press.user().isPaused() ? "Поставил на паузу" : "Снова работаю");
                }
                default -> menu.edit(press, screens.home(press.user()));
            }
        });
    }
}
