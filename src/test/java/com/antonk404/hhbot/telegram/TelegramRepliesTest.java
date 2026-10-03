package com.antonk404.hhbot.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegramRepliesTest {

    /** Запасная отправка: без тегов и под потолок Telegram, чтобы сообщение дошло хоть как-то. */
    @Test
    void fallbackTextIsPlainAndFits() {
        assertEquals("Java & Qt", TelegramReplies.plain("<b>Java &amp; Qt</b>"));
        assertEquals(TelegramReplies.MAX_TEXT, TelegramReplies.plain("<b>" + "x".repeat(5000) + "</b>").length());
    }
}
