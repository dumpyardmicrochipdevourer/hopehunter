package com.antonk404.hhbot.telegram.handler.dialog;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogHandlerTest {

    @Test
    void parsesNumbersTheWayPeopleTypeThem() {
        assertEquals(Optional.of(200000), DialogHandler.number("200 000", 1, 1_000_000));
        assertEquals(Optional.of(200000), DialogHandler.number("200_000", 1, 1_000_000));
        assertEquals(Optional.of(30), DialogHandler.number("30", 1, 100));
    }

    @Test
    void rejectsOutOfRangeAndGarbage() {
        assertEquals(Optional.empty(), DialogHandler.number("0", 1, 100));
        assertEquals(Optional.empty(), DialogHandler.number("101", 1, 100));
        assertEquals(Optional.empty(), DialogHandler.number("200к", 1, 1_000_000));
        assertEquals(Optional.empty(), DialogHandler.number("-5", 1, 100));
        assertEquals(Optional.empty(), DialogHandler.number("99999999999999", 1, 100));
    }
}
