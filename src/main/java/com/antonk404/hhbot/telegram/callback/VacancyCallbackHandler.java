package com.antonk404.hhbot.telegram.callback;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.hh.ApplyResult;
import com.antonk404.hhbot.hh.HhVacancy;
import com.antonk404.hhbot.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.ApplyService;
import com.antonk404.hhbot.telegram.Access;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.VacancyCards;
import com.antonk404.hhbot.telegram.VacancyPresenter;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Optional;

/** Кнопки на карточке вакансии. Data: {@code v:<id вакансии>:a} - откликнуться, {@code :s} - пропустить. */
@Component
public class VacancyCallbackHandler implements CallbackHandler {

    public static final String PREFIX = "v:";

    private final Access access;
    private final VacancyCards vacancyCards;
    private final ResponseRuleRepository responseRuleRepository;
    private final ApplyService applyService;
    private final TelegramReplies replies;

    public VacancyCallbackHandler(
            Access access,
            VacancyCards vacancyCards,
            ResponseRuleRepository responseRuleRepository,
            ApplyService applyService,
            TelegramReplies replies) {
        this.access = access;
        this.vacancyCards = vacancyCards;
        this.responseRuleRepository = responseRuleRepository;
        this.applyService = applyService;
        this.replies = replies;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith(PREFIX);
    }

    @Override
    public void handle(CallbackQuery query) {
        access.press(query).ifPresent(this::handle);
    }

    private void handle(Access.Press press) {
        BotUser user = press.user();
        String[] parts = press.data().split(":", 3);
        Optional<VacancyCards.Card> card = parts.length < 3
                ? Optional.empty()
                : vacancyCards.find(user.getId(), parts[1]);
        if (card.isEmpty()) {
            replies.answerCallback(press.queryId(), "карточка устарела");
            replies.editText(press.chatId(), press.messageId(), "Карточка устарела - вакансия была слишком давно.");
            return;
        }
        HhVacancy vacancy = card.get().vacancy();
        String title = VacancyPresenter.describe(vacancy) + "\n" + vacancy.url();

        Optional<ResponseRule> rule = responseRuleRepository.findByIdAndUserId(card.get().ruleId(), user.getId());
        if (rule.isEmpty()) {
            vacancyCards.drop(user.getId(), vacancy.id());
            replies.answerCallback(press.queryId(), "правила уже нет");
            replies.editText(press.chatId(), press.messageId(), "Правило удалено, откликнуться нечем.\n\n" + title);
            return;
        }

        if (parts[2].equals("s")) {
            applyService.skip(user, rule.get(), vacancy);
            close(press, vacancy, "пропустил", "⏭ Пропущено\n\n" + title);
            return;
        }

        ApplyResult result = applyService.apply(user, rule.get(), vacancy);
        switch (result) {
            case ApplyResult.Sent sent -> close(press, vacancy, "откликнулся", "✅ Откликнулся\n\n" + title);
            case ApplyResult.AlreadyApplied already ->
                    close(press, vacancy, "отклик уже был", "☑️ Отклик на неё уже был\n\n" + title);
            case ApplyResult.NeedsTest test ->
                    close(press, vacancy, "нужен тест", "✋ Нужно пройти тест - откликнись на сайте\n\n" + title);
            // Ниже карточка остаётся с кнопками: причина не в вакансии, и после её устранения
            // откликнуться можно тем же нажатием.
            case ApplyResult.LimitReached limit ->
                    replies.alert(press.queryId(), "hh больше не принимает отклики сегодня. Остановил всё до завтра.");
            case ApplyResult.Captcha captcha ->
                    replies.alert(press.queryId(), "hh показал капчу. Остановил отклики до завтра - "
                            + "зайди на сайт с браузера и проверь аккаунт.");
            case ApplyResult.SessionExpired expired ->
                    replies.alert(press.queryId(), "Сессия hh истекла. Подключи аккаунт заново: /menu → Аккаунт hh.");
            case ApplyResult.Failed failed -> replies.alert(press.queryId(), "Не вышло: " + failed.reason() + ".");
        }
    }

    /** Убирает кнопки: дело сделано, второе нажатие могло бы дать только «уже откликался». */
    private void close(Access.Press press, HhVacancy vacancy, String toast, String text) {
        vacancyCards.drop(press.user().getId(), vacancy.id());
        replies.answerCallback(press.queryId(), toast);
        replies.editText(press.chatId(), press.messageId(), text);
    }
}
