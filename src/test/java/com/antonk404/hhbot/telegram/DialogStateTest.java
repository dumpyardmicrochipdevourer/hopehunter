package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.InMemoryKeyValueStore;
import com.antonk404.hhbot.telegram.DialogState.Dialog;
import com.antonk404.hhbot.telegram.DialogState.Step;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogStateTest {

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private final DialogState state = new DialogState(store);

    @Test
    void remembersStepWithItsTarget() {
        state.expect(1L, Step.RULE_KEYWORDS, 42L);

        assertEquals(Optional.of(new Dialog(Step.RULE_KEYWORDS, "42")), state.current(1L));
        assertEquals(42L, state.current(1L).orElseThrow().id());
        assertEquals(Optional.empty(), state.current(2L));
    }

    /** Имя письма - свободный текст, в нём может быть что угодно, включая двоеточия. */
    @Test
    void argumentSurvivesPunctuation() {
        state.expect(1L, Step.LETTER_NEW_BODY, "Основное: v2");

        assertEquals("Основное: v2", state.current(1L).orElseThrow().arg());
    }

    @Test
    void expiresInsteadOfWaitingForever() {
        state.expect(1L, Step.ACCOUNT_COOKIES);

        assertEquals(Duration.ofMinutes(15), store.ttls.get("dlg:1"));
    }

    @Test
    void clearForgets() {
        state.expect(1L, Step.RULE_NEW);
        state.clear(1L);

        assertEquals(Optional.empty(), state.current(1L));
    }
}
