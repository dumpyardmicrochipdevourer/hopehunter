package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.dto.HhVacancy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacancyViewTest {

    private static final HhVacancy VACANCY = new HhVacancy(
            "42", "Java & Kotlin", "Acme <IT>", "от 200 000 ₽", "Москва", "https://hh.ru/vacancy/42", false, false);

    @Test
    void titleIsALinkAndUserTextIsEscaped() {
        assertEquals("<b><a href=\"https://hh.ru/vacancy/42\">Java &amp; Kotlin</a></b>\n"
                + "Acme &lt;IT&gt; · Москва\n💰 от 200 000 ₽", VacancyView.text(VACANCY));
    }

    @Test
    void omitsEmptyCityAndSalary() {
        assertEquals("<b>Java</b>\nAcme",
                VacancyView.text(new HhVacancy("1", "Java", "Acme", "", "", "", false, false)));
    }

    @Test
    void cardOffersLetterUntilItIsShown() {
        Screen closed = VacancyView.card("java", VACANCY, null);
        Screen opened = VacancyView.card("java", VACANCY, "Здравствуйте, <Acme>!");

        assertEquals(2, closed.keyboard().getKeyboard().size());
        assertEquals("v:42:l", closed.keyboard().getKeyboard().get(1).getFirst().getCallbackData());
        assertEquals(1, opened.keyboard().getKeyboard().size());
        assertTrue(opened.text().contains("<blockquote>Здравствуйте, &lt;Acme&gt;!</blockquote>"));
    }

    @Test
    void cardSaysWhenThereIsNoLetter() {
        Screen card = VacancyView.card("java", VACANCY, "");

        assertTrue(card.text().contains("уйдёт без него"));
        assertFalse(card.text().contains("<blockquote>"));
    }
}
