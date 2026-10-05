package com.antonk404.hhbot.telegram.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KbTest {

    /** Самая длинная data в боте - выбор резюме в мастере: хеш hh занимает 38 символов. */
    @Test
    void longestCallbackDataFitsTelegramLimit() {
        String hash = "a".repeat(38);

        assertDoesNotThrow(() -> Kb.btn("x", Cb.rule(Long.MAX_VALUE, "wrs:" + hash)));
        assertDoesNotThrow(() -> Kb.btn("x", Cb.vacancy("9999999999999", "a")));
    }

    @Test
    void oversizedCallbackDataFailsWhenTheScreenIsBuilt() {
        assertThrows(IllegalArgumentException.class, () -> Kb.btn("x", "r:1:rs:" + "я".repeat(40)));
    }

    @Test
    void fieldButtonShowsValueAndClipsLongOnes() {
        assertEquals("Регион: Москва", Kb.field("Регион", "Москва", "r:1:area").getText());
        assertEquals("Резюме: Ведущий разработчик с…",
                Kb.field("Резюме", "Ведущий разработчик серверной части", "r:1:resume").getText());
    }
}
