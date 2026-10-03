package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.InMemoryKeyValueStore;
import com.antonk404.hhbot.hh.HhResume;
import com.antonk404.hhbot.hh.HhVacancy;
import com.antonk404.hhbot.telegram.callback.VacancyCallbackHandler;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreensTest {

    /** Самая длинная data в боте - выбор резюме: хеш hh занимает 38 символов. */
    @Test
    void longestCallbackDataFitsTelegramLimit() {
        String hash = "a".repeat(38);
        String ruleId = String.valueOf(Long.MAX_VALUE);

        assertDoesNotThrow(() -> Kb.btn("x", "r:" + ruleId + ":rs:" + hash));
        assertDoesNotThrow(() -> Kb.btn("x", VacancyCallbackHandler.PREFIX + "9999999999999:a"));
    }

    @Test
    void oversizedCallbackDataFailsWhenTheScreenIsBuilt() {
        assertThrows(IllegalArgumentException.class, () -> Kb.btn("x", "r:1:rs:" + "я".repeat(40)));
    }

    @Test
    void describesVacancyWithoutEmptyLines() {
        assertEquals("Java\nAcme · Москва\nот 200 000 ₽", VacancyPresenter.describe(
                new HhVacancy("1", "Java", "Acme", "от 200 000 ₽", "Москва", "u", false, false)));
        assertEquals("Java\nAcme", VacancyPresenter.describe(
                new HhVacancy("1", "Java", "Acme", "", "", "u", false, false)));
    }

    @Test
    void cardRoundTripsAndIsScopedToItsUser() {
        VacancyCards cards = new VacancyCards(new InMemoryKeyValueStore());
        HhVacancy vacancy = new HhVacancy("42", "Java", "Acme", "", "Москва", "https://hh.ru/vacancy/42", true, false);

        cards.stage(1L, 7L, vacancy);

        assertEquals(Optional.of(new VacancyCards.Card(7L, vacancy)), cards.find(1L, "42"));
        assertEquals(Optional.empty(), cards.find(2L, "42"));
        cards.drop(1L, "42");
        assertEquals(Optional.empty(), cards.find(1L, "42"));
    }

    @Test
    void clipsOverlongText() {
        String clipped = TelegramReplies.clip("x".repeat(5000));

        assertEquals(TelegramReplies.MAX_TEXT, clipped.length());
        assertEquals("ok", TelegramReplies.clip("ok"));
    }

    @Test
    void resumeTitleDoesNotBreakTheButton() {
        assertDoesNotThrow(() -> Kb.btn(new HhResume("h", "Java-разработчик").title(), "r:1:rs:h"));
    }
}
