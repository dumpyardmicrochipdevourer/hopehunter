package com.antonk404.hhbot.telegram.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlTest {

    /** Названия вакансий пишут люди: «C++ & Qt <middle>» не должно ронять отправку сообщения. */
    @Test
    void escapesWhatTelegramWouldRejectOrInterpret() {
        assertEquals("C++ &amp; Qt &lt;middle&gt;", Html.esc("C++ & Qt <middle>"));
        assertEquals("<b>R&amp;D</b>", Html.b("R&D"));
        assertEquals("", Html.esc(null));
    }

    @Test
    void linkEscapesBothTextAndAddress() {
        assertEquals("<a href=\"https://hh.ru/vacancy/1?a=1&amp;b=&quot;2&quot;\">A&amp;B</a>",
                Html.link("A&B", "https://hh.ru/vacancy/1?a=1&b=\"2\""));
    }

    @Test
    void plainUndoesMarkupForTheFallbackSend() {
        assertEquals("Java & Kotlin <dev>", Html.plain("<b>Java &amp; Kotlin</b> <i>&lt;dev&gt;</i>"));
        assertEquals("a < b", Html.plain(Html.esc("a < b")));
    }

    @Test
    void clipsBeforeMarkupNotAfter() {
        assertEquals("абв…", Html.clip("абвгде", 4));
        assertEquals("абв", Html.clip("абв", 4));
    }
}
