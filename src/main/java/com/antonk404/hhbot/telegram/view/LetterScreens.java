package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.service.util.LetterRenderer;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.antonk404.hhbot.telegram.view.Kb.btn;

/** Раздел «Письма». */
@Component
public class LetterScreens {

    /** Тело письма на экране. Сам шаблон бывает до 4000 символов - целиком с заголовком он в сообщение не влезет. */
    private static final int BODY_ON_SCREEN = 3000;

    private final LetterTemplateRepository letterTemplateRepository;

    public LetterScreens(LetterTemplateRepository letterTemplateRepository) {
        this.letterTemplateRepository = letterTemplateRepository;
    }

    public Screen list(BotUser user) {
        Kb keyboard = Kb.of();
        List<LetterTemplate> templates = letterTemplateRepository.findByUserIdOrderByIdAsc(user.getId());
        templates.forEach(template -> keyboard.row(btn("✉️ " + template.getName(), Cb.letter(template.getId(), "show"))));
        String text = "<b>Письма</b>\n\n" + (templates.isEmpty()
                ? "Пока ни одного. Письмо - шаблон сопроводительного: пишешь один раз, а название вакансии "
                + "и компании я подставляю сам."
                : "Шаблоны сопроводительных писем. Какое прикладывать - выбирается в правиле.");
        return new Screen(text, keyboard
                .row(btn("➕ Новое письмо", Cb.LETTER_NEW))
                .row(btn("🏠 Меню", Cb.HOME))
                .build());
    }

    public Screen letter(LetterTemplate template) {
        Long id = template.getId();
        return new Screen("✉️ " + Html.b(template.getName()) + "\n\n"
                + Html.quote(Html.clip(template.getBody(), BODY_ON_SCREEN)), Kb.of()
                .row(btn("👁 Как оно уйдёт", Cb.letter(id, "pv")))
                .row(btn("✏️ Изменить текст", Cb.letter(id, "edit")), btn("🗑 Удалить", Cb.letter(id, "del")))
                .row(btn("« К письмам", Cb.LETTERS))
                .build());
    }

    public Screen preview(LetterTemplate template, HhVacancy vacancy, String ownerName) {
        String rendered = LetterRenderer.render(template.getBody(), vacancy, ownerName);
        return new Screen("<b>Так письмо уйдёт</b> на вакансию «" + Html.esc(vacancy.name()) + "», "
                + Html.esc(vacancy.company()) + ":\n\n" + Html.quote(Html.clip(rendered, BODY_ON_SCREEN)),
                Kb.of().row(btn("« Назад", Cb.letter(template.getId(), "show"))).build());
    }

    /** Подсказка по алиасам. Моноширинные - чтобы вставлялись в текст тапом, без опечаток. */
    public static String aliasHelp() {
        return """
                Что я подставлю сам:
                <code>[company_name]</code> - компания
                <code>[vacancy_name]</code> - название вакансии
                <code>[salary]</code> - зарплата из вакансии, если указана
                <code>[city]</code> - город
                <code>[my_name]</code> - твоё имя из hh

                Например:
                <i>Здравствуйте! Меня заинтересовала вакансия [vacancy_name] в [company_name]…</i>""";
    }

    /** Имена алиасов через запятую - для сообщения об опечатке в шаблоне. */
    public static String aliasList() {
        return "[" + String.join("], [", LetterRenderer.ALIASES) + "]";
    }
}
