package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.dto.HhVacancy;

import static com.antonk404.hhbot.telegram.view.Kb.btn;

/** Как вакансия выглядит в чате: строками в списке и карточкой с кнопками. */
public final class VacancyView {

    private static final int LETTER_ON_CARD = 2500;

    private VacancyView() {
    }

    /** Название ссылкой на hh, под ним компания, город и зарплата. */
    public static String text(HhVacancy vacancy) {
        StringBuilder out = new StringBuilder("<b>")
                .append(vacancy.url().isBlank() ? Html.esc(vacancy.name()) : Html.link(vacancy.name(), vacancy.url()))
                .append("</b>\n")
                .append(Html.esc(vacancy.company()));
        if (!vacancy.city().isEmpty()) {
            out.append(" · ").append(Html.esc(vacancy.city()));
        }
        if (!vacancy.salary().isEmpty()) {
            out.append("\n💰 ").append(Html.esc(vacancy.salary()));
        }
        return out.toString();
    }

    /**
     * Карточка для режима подтверждения.
     *
     * @param letter письмо, если человек попросил его показать; null - не показывать
     */
    public static Screen card(String ruleName, HhVacancy vacancy, String letter) {
        StringBuilder text = new StringBuilder("🔎 ").append(Html.esc(ruleName)).append("\n\n").append(text(vacancy));
        if (vacancy.letterRequired()) {
            text.append("\n<i>работодатель просит сопроводительное письмо</i>");
        }
        Kb keyboard = Kb.of().row(
                btn("✅ Откликнуться", Cb.vacancy(vacancy.id(), "a")),
                btn("⏭ Пропустить", Cb.vacancy(vacancy.id(), "s")));
        if (letter == null) {
            keyboard.row(btn("✉️ Показать письмо", Cb.vacancy(vacancy.id(), "l")));
        } else if (letter.isEmpty()) {
            text.append("\n\n✉️ <i>В правиле не выбрано письмо - отклик уйдёт без него.</i>");
        } else {
            text.append("\n\n✉️ С откликом уйдёт:\n").append(Html.quote(Html.clip(letter, LETTER_ON_CARD)));
        }
        return new Screen(text.toString(), keyboard.build());
    }
}
