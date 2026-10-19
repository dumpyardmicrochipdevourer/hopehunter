package com.antonk404.hhbot.telegram.handler.callback;

import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.service.CaptchaService;
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
    private final CaptchaService captchaService;
    private final DialogState dialogState;
    private final MenuMessage menu;
    private final MenuScreens screens;

    public MenuCallbackHandler(
            Access access,
            WhitelistService whitelistService,
            CaptchaService captchaService,
            DialogState dialogState,
            MenuMessage menu,
            MenuScreens screens) {
        this.access = access;
        this.whitelistService = whitelistService;
        this.captchaService = captchaService;
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
                case Cb.CAPTCHA -> captcha(press);
                default -> menu.edit(press, screens.home(press.user()));
            }
        });
    }

    /** Человек говорит, что прошёл капчу; верим не слову, а ответу hh на одну страницу. */
    private void captcha(Access.Press press) {
        menu.progress(press, "Спрашиваю hh, пускает ли он…");
        try {
            switch (captchaService.recheck(press.user())) {
                case GONE -> menu.finish(press, screens.home(press.user()));
                case STILL_THERE -> menu.finish(press, MenuScreens.captcha("hh всё ещё показывает капчу"));
                case TOO_SOON -> menu.finish(press, MenuScreens.captcha("Подожди минуту и нажми ещё раз"));
                case NOTHING_TO_LIFT -> menu.finish(press, screens.home(press.user()));
                case SESSION_EXPIRED -> menu.finish(press, MenuScreens.problem("hh разлогинил сессию.",
                        "🔑 Подключить заново", Cb.CONNECT, Cb.HOME));
            }
        } catch (HhException e) {
            menu.finish(press, MenuScreens.problem("hh сейчас не отвечает.", "🔄 Ещё раз", Cb.CAPTCHA, Cb.HOME));
        }
    }
}
