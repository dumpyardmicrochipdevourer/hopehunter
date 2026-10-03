package com.antonk404.hhbot.telegram.handler.callback;

import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.MenuMessage;
import com.antonk404.hhbot.telegram.view.AccountScreens;
import com.antonk404.hhbot.telegram.view.Cb;
import com.antonk404.hhbot.telegram.view.MenuScreens;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/** Раздел «Аккаунт hh»: подключение сессии, список резюме, отключение. */
@Component
public class AccountCallbackHandler implements CallbackHandler {

    private final Access access;
    private final HhAccountService hhAccountService;
    private final DialogState dialogState;
    private final MenuMessage menu;
    private final AccountScreens screens;

    public AccountCallbackHandler(
            Access access,
            HhAccountService hhAccountService,
            DialogState dialogState,
            MenuMessage menu,
            AccountScreens screens) {
        this.access = access;
        this.hhAccountService = hhAccountService;
        this.dialogState = dialogState;
        this.menu = menu;
        this.screens = screens;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith("acc:");
    }

    @Override
    public void handle(CallbackQuery query) {
        access.press(query).ifPresent(this::handle);
    }

    private void handle(Access.Press press) {
        Long userId = press.user().getId();
        dialogState.clear(userId);
        String data = press.data();

        if (data.equals(Cb.CONNECT)) {
            // Ждём куку уже с первого экрана: кто-то пришлёт её, не дочитав до выбора браузера.
            dialogState.expect(userId, DialogState.Step.ACCOUNT_COOKIES);
            menu.edit(press, screens.connectIntro());
        } else if (data.startsWith(Cb.CONNECT_HOW)) {
            String browser = data.substring(Cb.CONNECT_HOW.length());
            dialogState.expect(userId, DialogState.Step.ACCOUNT_COOKIES);
            menu.edit(press, screens.connectSteps(AccountScreens.isBrowser(browser) ? browser : "chrome"));
        } else if (data.equals(Cb.LOGOUT)) {
            hhAccountService.disconnect(userId);
            menu.edit(press, screens.account(press.user()), "Отключил");
        } else if (data.equals(Cb.RESUMES)) {
            resumes(press);
        } else {
            menu.edit(press, screens.account(press.user()));
        }
    }

    private void resumes(Access.Press press) {
        menu.progress(press, "Спрашиваю у hh твои резюме…");
        try {
            menu.finish(press, screens.resumes(hhAccountService.profile(press.user().getId()).resumes()));
        } catch (HhSessionExpiredException e) {
            menu.finish(press, screens.account(press.user()));
        } catch (HhException e) {
            menu.finish(press, MenuScreens.problem("hh сейчас не отвечает.", "🔄 Попробовать ещё раз",
                    Cb.RESUMES, Cb.ACCOUNT));
        }
    }
}
