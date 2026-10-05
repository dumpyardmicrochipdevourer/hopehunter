package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.service.ScanListener;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.state.VacancyCards;
import org.springframework.stereotype.Component;

import static com.antonk404.hhbot.telegram.view.Kb.btn;

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
        replies.menu(user.getChatId(), VacancyView.card(rule.getName(), vacancy, null));
    }

    @Override
    public void applied(BotUser user, ResponseRule rule, HhVacancy vacancy) {
        replies.text(user.getChatId(),
                "✅ <b>Откликнулся</b> · " + Html.esc(rule.getName()) + "\n\n" + VacancyView.text(vacancy));
    }

    @Override
    public void manual(BotUser user, ResponseRule rule, HhVacancy vacancy, String reason) {
        replies.text(user.getChatId(), "✋ <b>Откликнись сам</b> · " + Html.esc(rule.getName()) + "\n"
                + "Я не смог: " + Html.esc(reason) + ".\n\n" + VacancyView.text(vacancy));
    }

    @Override
    public void stopped(BotUser user, String reason) {
        replies.menu(user.getChatId(), new Screen(
                "⛔ <b>Отклики остановлены до завтра</b>\n\n" + Html.esc(capitalize(reason)) + ".",
                Kb.of().row(Kb.link("Открыть hh.ru", "https://hh.ru/applicant/negotiations")).build()));
    }

    @Override
    public void sessionExpired(BotUser user) {
        replies.menu(user.getChatId(), new Screen(
                "🔑 <b>hh разлогинил сессию</b>",
                Kb.of().row(btn("🔑 Подключить заново", Cb.CONNECT)).build()));
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
