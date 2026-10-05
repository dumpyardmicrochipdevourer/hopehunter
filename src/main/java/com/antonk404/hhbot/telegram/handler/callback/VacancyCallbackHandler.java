package com.antonk404.hhbot.telegram.handler.callback;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.ApplyService;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.VacancyCards;
import com.antonk404.hhbot.telegram.view.Cb;
import com.antonk404.hhbot.telegram.view.VacancyPresenter;
import com.antonk404.hhbot.telegram.view.VacancyView;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Optional;

/**
 * Кнопки на карточке вакансии: откликнуться, пропустить, показать письмо.
 *
 * <p>Карточка - отдельное сообщение, не меню, поэтому правится напрямую через
 * {@link TelegramReplies}, мимо {@code MenuMessage}: иначе карточка заняла бы место меню.
 */
@Component
public class VacancyCallbackHandler implements CallbackHandler {

    private final Access access;
    private final VacancyCards vacancyCards;
    private final ResponseRuleRepository responseRuleRepository;
    private final ApplyService applyService;
    private final VacancyPresenter vacancyPresenter;
    private final TelegramReplies replies;

    public VacancyCallbackHandler(
            Access access,
            VacancyCards vacancyCards,
            ResponseRuleRepository responseRuleRepository,
            ApplyService applyService,
            VacancyPresenter vacancyPresenter,
            TelegramReplies replies) {
        this.access = access;
        this.vacancyCards = vacancyCards;
        this.responseRuleRepository = responseRuleRepository;
        this.applyService = applyService;
        this.vacancyPresenter = vacancyPresenter;
        this.replies = replies;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith(Cb.VACANCY);
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
            replies.answerCallback(press.queryId(), "Карточка устарела");
            replies.editText(press.chatId(), press.messageId(), "<i>Карточка устарела - вакансия была слишком давно.</i>");
            return;
        }
        HhVacancy vacancy = card.get().vacancy();
        String title = VacancyView.text(vacancy);

        Optional<ResponseRule> found = responseRuleRepository.findByIdAndUserId(card.get().ruleId(), user.getId());
        if (found.isEmpty()) {
            vacancyCards.drop(user.getId(), vacancy.id());
            replies.answerCallback(press.queryId(), "Правила уже нет");
            replies.editText(press.chatId(), press.messageId(),
                    "<i>Правило удалено - откликнуться нечем.</i>\n\n" + title);
            return;
        }
        ResponseRule rule = found.get();

        switch (parts[2]) {
            case "s" -> {
                applyService.skip(user, rule, vacancy);
                close(press, vacancy, "Пропустил", "⏭ <b>Пропущено</b>\n\n" + title);
            }
            case "l" -> {
                replies.answerCallback(press.queryId(), null);
                replies.editMenu(press.chatId(), press.messageId(),
                        VacancyView.card(rule.getName(), vacancy, applyService.letter(user, rule, vacancy)));
            }
            default -> apply(press, rule, vacancy, title);
        }
    }

    private void apply(Access.Press press, ResponseRule rule, HhVacancy vacancy, String title) {
        BotUser user = press.user();
        ApplyResult result = applyService.apply(user, rule, vacancy);
        switch (result) {
            case ApplyResult.Sent sent -> close(press, vacancy, "Откликнулся", "✅ <b>Откликнулся</b>\n\n" + title);
            case ApplyResult.AlreadyApplied already ->
                    close(press, vacancy, "Отклик уже был", "☑️ <b>Отклик на неё уже был</b>\n\n" + title);
            case ApplyResult.NeedsTest test -> close(press, vacancy, "Нужен тест",
                    "✋ <b>Работодатель просит пройти тест</b> - откликнись на сайте.\n\n" + title);
            // Ниже карточка остаётся с кнопками: причина не в вакансии, и после её устранения
            // откликнуться можно тем же нажатием.
            case ApplyResult.LimitReached limit -> {
                replies.answerCallback(press.queryId(), null);
                vacancyPresenter.stopped(user, "hh больше не принимает отклики сегодня");
            }
            case ApplyResult.Captcha captcha -> {
                replies.answerCallback(press.queryId(), null);
                vacancyPresenter.stopped(user, "hh показал капчу");
            }
            case ApplyResult.SessionExpired expired -> {
                replies.answerCallback(press.queryId(), null);
                vacancyPresenter.sessionExpired(user);
            }
            case ApplyResult.Failed failed ->
                    replies.alert(press.queryId(), "Не получилось: " + failed.reason() + ". Попробуй ещё раз чуть позже.");
        }
    }

    /** Убирает кнопки: дело сделано, второе нажатие могло бы дать только «уже откликался». */
    private void close(Access.Press press, HhVacancy vacancy, String toast, String html) {
        vacancyCards.drop(press.user().getId(), vacancy.id());
        replies.answerCallback(press.queryId(), toast);
        replies.editText(press.chatId(), press.messageId(), html);
    }
}
