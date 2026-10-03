package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.telegram.handler.callback.VacancyCallbackHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KbTest {

    /** Самая длинная data в боте - выбор резюме: хеш hh занимает 38 символов. */
    @Test
    void longestCallbackDataFitsTelegramLimit() {
        String hash = "a".repeat(38);

        assertDoesNotThrow(() -> Kb.btn("x", Screens.rule(Long.MAX_VALUE, "rs:" + hash)));
        assertDoesNotThrow(() -> Kb.btn("x", VacancyCallbackHandler.PREFIX + "9999999999999:a"));
    }

    @Test
    void oversizedCallbackDataFailsWhenTheScreenIsBuilt() {
        assertThrows(IllegalArgumentException.class, () -> Kb.btn("x", "r:1:rs:" + "я".repeat(40)));
    }
}
