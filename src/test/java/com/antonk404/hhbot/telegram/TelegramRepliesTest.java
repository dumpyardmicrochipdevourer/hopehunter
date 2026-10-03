package com.antonk404.hhbot.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegramRepliesTest {

    @Test
    void clipsOverlongText() {
        String clipped = TelegramReplies.clip("x".repeat(5000));

        assertEquals(TelegramReplies.MAX_TEXT, clipped.length());
        assertEquals("ok", TelegramReplies.clip("ok"));
    }
}
