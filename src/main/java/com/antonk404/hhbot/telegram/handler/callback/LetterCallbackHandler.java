package com.antonk404.hhbot.telegram.handler.callback;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.VacancyScanner;
import com.antonk404.hhbot.service.util.LetterRenderer;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.DialogState.Step;
import com.antonk404.hhbot.telegram.view.Kb;
import com.antonk404.hhbot.telegram.view.Screen;
import com.antonk404.hhbot.telegram.view.Screens;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Optional;

/** Раздел «Письма». Data: {@code l:<id>:<действие>}. */
@Component
public class LetterCallbackHandler implements CallbackHandler {

    /** На чём показать предпросмотр, если настоящую вакансию достать не вышло. */
    private static final HhVacancy SAMPLE = new HhVacancy(
            "0", "Java-разработчик", "Рога и копыта", "от 200 000 до 300 000 ₽", "Москва", "", false, false);

    private final Access access;
    private final LetterTemplateRepository letterTemplateRepository;
    private final ResponseRuleRepository responseRuleRepository;
    private final HhAccountService hhAccountService;
    private final VacancyScanner vacancyScanner;
    private final DialogState dialogState;
    private final Screens screens;
    private final TelegramReplies replies;

    public LetterCallbackHandler(
            Access access,
            LetterTemplateRepository letterTemplateRepository,
            ResponseRuleRepository responseRuleRepository,
            HhAccountService hhAccountService,
            VacancyScanner vacancyScanner,
            DialogState dialogState,
            Screens screens,
            TelegramReplies replies) {
        this.access = access;
        this.letterTemplateRepository = letterTemplateRepository;
        this.responseRuleRepository = responseRuleRepository;
        this.hhAccountService = hhAccountService;
        this.vacancyScanner = vacancyScanner;
        this.dialogState = dialogState;
        this.screens = screens;
        this.replies = replies;
    }

    @Override
    public boolean supports(String data) {
        return data.startsWith("l:");
    }

    @Override
    public void handle(CallbackQuery query) {
        access.press(query).ifPresent(this::handle);
    }

    private void handle(Access.Press press) {
        BotUser user = press.user();
        dialogState.clear(user.getId());

        if (press.data().equals(Screens.LETTERS)) {
            show(press, screens.letters(user));
            return;
        }
        if (press.data().equals(Screens.LETTER_NEW)) {
            dialogState.expect(user.getId(), Step.LETTER_NEW_NAME);
            show(press, screens.prompt("Как назвать письмо? Название видишь только ты.", Screens.LETTERS));
            return;
        }

        String[] parts = press.data().split(":", 3);
        Optional<LetterTemplate> found = Optional.empty();
        if (parts.length == 3) {
            try {
                found = letterTemplateRepository.findByIdAndUserId(Long.parseLong(parts[1]), user.getId());
            } catch (NumberFormatException e) {
                // подделанная data - ведём себя так же, как с удалённым письмом
            }
        }
        if (found.isEmpty()) {
            replies.answerCallback(press.queryId(), "письма уже нет");
            replies.editMenu(press.chatId(), press.messageId(), screens.letters(user));
            return;
        }
        LetterTemplate template = found.get();

        switch (parts[2]) {
            case "edit" -> {
                dialogState.expect(user.getId(), Step.LETTER_BODY, template.getId());
                show(press, screens.prompt("Пришли новый текст письма.\n\n" + Screens.aliasHelp(),
                        Screens.letter(template.getId(), "show")));
            }
            case "pv" -> {
                HhVacancy vacancy = sampleVacancy(user);
                String ownerName = hhAccountService.session(user.getId()).map(HhSession::getOwnerName).orElse("");
                String text = "Так письмо уйдёт на вакансию «" + vacancy.name() + "» (" + vacancy.company() + "):\n\n"
                        + LetterRenderer.render(template.getBody(), vacancy, ownerName);
                show(press, new Screen(text,
                        Kb.of().row(Kb.btn("« Назад", Screens.letter(template.getId(), "show"))).build()));
            }
            case "del" -> {
                // Правила, которые ссылались на письмо, остаются рабочими - просто без письма.
                for (ResponseRule rule : responseRuleRepository.findByLetterTemplateId(template.getId())) {
                    rule.setLetterTemplateId(null);
                    responseRuleRepository.save(rule);
                }
                letterTemplateRepository.delete(template);
                replies.answerCallback(press.queryId(), "удалил");
                replies.editMenu(press.chatId(), press.messageId(), screens.letters(user));
            }
            default -> show(press, screens.letter(template));
        }
    }

    /** Первая вакансия по первому настроенному правилу; выдуманная, если такой нет или hh молчит. */
    private HhVacancy sampleVacancy(BotUser user) {
        Optional<ResponseRule> rule = responseRuleRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
                .filter(candidate -> candidate.getKeywords() != null && !candidate.getKeywords().isBlank())
                .findFirst();
        if (rule.isEmpty()) {
            return SAMPLE;
        }
        try {
            return vacancyScanner.preview(user, rule.get()).stream().findFirst().orElse(SAMPLE);
        } catch (HhException e) {
            return SAMPLE;
        }
    }

    private void show(Access.Press press, Screen screen) {
        replies.answerCallback(press.queryId(), null);
        replies.editMenu(press.chatId(), press.messageId(), screen);
    }
}
