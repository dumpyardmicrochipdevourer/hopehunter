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
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.state.DialogState;
import com.antonk404.hhbot.telegram.state.DialogState.Step;
import com.antonk404.hhbot.telegram.state.MenuMessage;
import com.antonk404.hhbot.telegram.view.Cb;
import com.antonk404.hhbot.telegram.view.Html;
import com.antonk404.hhbot.telegram.view.Kb;
import com.antonk404.hhbot.telegram.view.LetterScreens;
import com.antonk404.hhbot.telegram.view.MenuScreens;
import com.antonk404.hhbot.telegram.view.Screen;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Optional;

/** Раздел «Письма». Data: {@code l:<id>:<действие>}. */
@Component
public class LetterCallbackHandler implements CallbackHandler {

    public static final String NEW_LETTER_PROMPT = "<b>Новое письмо · шаг 1 из 2</b>\n\nНазвание:";

    /** На чём показать предпросмотр, если настоящую вакансию достать не вышло. */
    private static final HhVacancy SAMPLE = new HhVacancy(
            "0", "DevOps Engineer", "Amazon", "от 200 000 до 300 000 ₽", "Москва", "", false, false);

    private final Access access;
    private final LetterTemplateRepository letterTemplateRepository;
    private final ResponseRuleRepository responseRuleRepository;
    private final HhAccountService hhAccountService;
    private final VacancyScanner vacancyScanner;
    private final DialogState dialogState;
    private final MenuMessage menu;
    private final LetterScreens screens;

    public LetterCallbackHandler(
            Access access,
            LetterTemplateRepository letterTemplateRepository,
            ResponseRuleRepository responseRuleRepository,
            HhAccountService hhAccountService,
            VacancyScanner vacancyScanner,
            DialogState dialogState,
            MenuMessage menu,
            LetterScreens screens) {
        this.access = access;
        this.letterTemplateRepository = letterTemplateRepository;
        this.responseRuleRepository = responseRuleRepository;
        this.hhAccountService = hhAccountService;
        this.vacancyScanner = vacancyScanner;
        this.dialogState = dialogState;
        this.menu = menu;
        this.screens = screens;
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

        if (press.data().equals(Cb.LETTERS)) {
            menu.edit(press, screens.list(user));
            return;
        }
        if (press.data().equals(Cb.LETTER_NEW)) {
            dialogState.expect(user.getId(), Step.LETTER_NEW_NAME);
            menu.edit(press, MenuScreens.prompt(NEW_LETTER_PROMPT, Cb.LETTERS));
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
            menu.edit(press, screens.list(user), "Этого письма уже нет");
            return;
        }
        LetterTemplate template = found.get();
        Long id = template.getId();

        switch (parts[2]) {
            case "edit" -> {
                dialogState.expect(user.getId(), Step.LETTER_BODY, id);
                menu.edit(press, MenuScreens.prompt("<b>Новый текст письма «" + Html.esc(template.getName())
                        + "»</b>\n\n" + LetterScreens.aliasHelp(), Cb.letter(id, "show")));
            }
            case "pv" -> {
                menu.progress(press, "Подбираю вакансию для примера…");
                String ownerName = hhAccountService.session(user.getId()).map(HhSession::getOwnerName).orElse("");
                menu.finish(press, screens.preview(template, sampleVacancy(user), ownerName));
            }
            case "del" -> menu.edit(press, new Screen(
                    "Удалить письмо «" + Html.esc(template.getName()) + "»?",
                    Kb.of().row(Kb.btn("🗑 Да, удалить", Cb.letter(id, "delok")), Kb.btn("Нет", Cb.letter(id, "show")))
                            .build()));
            case "delok" -> {
                for (ResponseRule rule : responseRuleRepository.findByLetterTemplateId(id)) {
                    rule.setLetterTemplateId(null);
                    responseRuleRepository.save(rule);
                }
                letterTemplateRepository.delete(template);
                menu.edit(press, screens.list(user), "Удалил");
            }
            default -> menu.edit(press, screens.letter(template));
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
}
