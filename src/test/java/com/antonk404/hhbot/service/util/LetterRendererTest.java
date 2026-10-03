package com.antonk404.hhbot.service.util;

import com.antonk404.hhbot.domain.dto.HhVacancy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LetterRendererTest {

    private static final HhVacancy WITH_SALARY = new HhVacancy(
            "1", "Java-разработчик", "Максилект", "от 230 000 до 310 000 ₽", "Москва",
            "https://hh.ru/vacancy/1", false, false);

    private static final HhVacancy NO_SALARY = new HhVacancy(
            "2", "DevOps", "Банк ПСБ, ИТ", "", "Москва", "https://hh.ru/vacancy/2", false, false);

    @Test
    void substitutesEveryAlias() {
        String letter = LetterRenderer.render(
                "Здравствуйте! Откликаюсь на [vacancy_name] в [company_name] ([city], [salary]). [my_name]",
                WITH_SALARY, "Антон");

        assertEquals("Здравствуйте! Откликаюсь на Java-разработчик в Максилект "
                + "(Москва, от 230 000 до 310 000 ₽). Антон", letter);
    }

    @Test
    void emptyValueLeavesNoStraySpaces() {
        String letter = LetterRenderer.render("На зарплату [salary] согласен, [my_name] .", NO_SALARY, null);

        assertEquals("На зарплату согласен,.", letter);
    }

    @Test
    void keepsParagraphBreaks() {
        String letter = LetterRenderer.render("Добрый день!\n\nХочу в [company_name].\n", NO_SALARY, "");

        assertEquals("Добрый день!\n\nХочу в Банк ПСБ, ИТ.", letter);
    }

    @Test
    void valueWithDollarOrBackslashIsInsertedLiterally() {
        HhVacancy tricky = new HhVacancy("3", "C$1 \\dev", "A", "", "", "u", false, false);

        assertEquals("C$1 \\dev", LetterRenderer.render("[vacancy_name]", tricky, ""));
    }

    @Test
    void reportsUnknownAliasesOnce() {
        assertEquals(java.util.List.of("compani_name", "foo"),
                LetterRenderer.unknownAliases("[compani_name] [foo] [compani_name] [city]"));
    }

    @Test
    void bracketsThatAreNotAliasesAreLeftAlone() {
        assertEquals(java.util.List.of(), LetterRenderer.unknownAliases("см. [1] и [Резюме]"));
        assertEquals("см. [1] и [Резюме]", LetterRenderer.render("см. [1] и [Резюме]", NO_SALARY, ""));
    }
}
