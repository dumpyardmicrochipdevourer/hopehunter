package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.service.ScanListener;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.handler.callback.AccountCallbackHandler;
import com.antonk404.hhbot.telegram.handler.callback.VacancyCallbackHandler;
import com.antonk404.hhbot.telegram.state.VacancyCards;
import org.springframework.stereotype.Component;

/** Превращает находки сканера в сообщения. */
@Component
public class VacancyPresenter implements ScanListener {

    private final TelegramReplies replies;
    private final VacancyCards vacancyCards;

    public VacancyPresenter(TelegramReplies replies, VacancyCards vacancyCards) {
        this.replies = replies;
        this.vacancyCards = vacancyCards;
    }

    @Override
    public void found(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        vacancyCards.stage(user.getId(), rule.getId(), vacancy);
        replies.menu(user.getChatId(), new Screen(
                "🔎 " + rule.getName() + "\n\n" + describe(vacancy),
                Kb.of()
                        .row(Kb.btn("✅ Откликнуться", VacancyCallbackHandler.PREFIX + vacancy.id() + ":a"),
                                Kb.btn("⏭ Пропустить", VacancyCallbackHandler.PREFIX + vacancy.id() + ":s"))
                        .row(Kb.link("Открыть на hh", vacancy.url()))
                        .build()));
    }

    @Override
    public void applied(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        replies.text(user.getChatId(), "✅ Откликнулся · " + rule.getName() + "\n\n" + describe(vacancy)
                + "\n" + vacancy.url());
    }

    @Override
    public void manual(BotUser user, ResponseRule rule, HhVacancy vacancy, String reason) {
        replies.text(user.getChatId(), "✋ Сам не смог: " + reason + " · " + rule.getName() + "\n\n"
                + describe(vacancy) + "\n" + vacancy.url());
    }

    @Override
    public void stopped(BotUser user, String reason) {
        replies.text(user.getChatId(), "⛔ Остановил отклики до завтра: " + reason + ".\n"
                + "Зайди на hh.ru с браузера и убедись, что аккаунт в порядке.");
    }

    @Override
    public void sessionExpired(BotUser user) {
        replies.menu(user.getChatId(), new Screen(
                "🔑 hh больше не узнаёт сессию. Отклики стоят, пока не войдёшь заново.",
                Kb.of().row(Kb.btn("Войти заново", AccountCallbackHandler.COOKIES)).build()));
    }

    public static String describe(HhVacancy vacancy) {
        StringBuilder out = new StringBuilder(vacancy.name()).append('\n').append(vacancy.company());
        if (!vacancy.city().isEmpty()) {
            out.append(" · ").append(vacancy.city());
        }
        if (!vacancy.salary().isEmpty()) {
            out.append('\n').append(vacancy.salary());
        }
        return out.toString();
    }
}
