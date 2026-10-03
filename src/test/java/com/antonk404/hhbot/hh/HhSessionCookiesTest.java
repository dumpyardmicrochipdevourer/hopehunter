package com.antonk404.hhbot.hh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HhSessionCookiesTest {

    @Test
    void parsesCookieHeader() {
        HhSessionCookies cookies = HhSessionCookies.parse("Cookie: hhuid=u1; hhtoken=tok==; _xsrf=x1");

        assertEquals("hhuid=u1; hhtoken=tok==; _xsrf=x1", cookies.header());
        assertEquals("x1", cookies.xsrf());
    }

    @Test
    void bareValueIsTheToken() {
        HhSessionCookies cookies = HhSessionCookies.parse("  abc!def  ");

        assertTrue(cookies.header().startsWith("hhtoken=abc!def; _xsrf="));
        assertEquals(32, cookies.xsrf().length());
    }

    /** Всё это люди реально копируют из браузера, когда их просят «значение hhtoken». */
    @Test
    void acceptsEveryShapeTheBrowserGives() {
        String expected = "hhtoken=abc!def";

        assertTrue(HhSessionCookies.parse("\"abc!def\"").header().startsWith(expected + ";"));
        assertTrue(HhSessionCookies.parse("`abc!def`").header().startsWith(expected + ";"));
        assertTrue(HhSessionCookies.parse("hhtoken: abc!def").header().startsWith(expected + ";"));
        assertTrue(HhSessionCookies.parse("hhtoken\tabc!def\t.hh.ru\t/\t2027-10-03").header().startsWith(expected + ";"));
        assertTrue(HhSessionCookies.parse("hhtoken=\"abc!def\"; _xsrf=x1").header().startsWith(expected + ";"));
    }

    @Test
    void headerRoundTripsThroughParse() {
        HhSessionCookies cookies = HhSessionCookies.parse("abc");

        assertEquals(cookies.header(), HhSessionCookies.parse(cookies.header()).header());
    }

    @Test
    void rejectsTextWithoutToken() {
        assertThrows(IllegalArgumentException.class, () -> HhSessionCookies.parse("hhuid=u1; _xsrf=x1"));
        assertThrows(IllegalArgumentException.class, () -> HhSessionCookies.parse(""));
        assertThrows(IllegalArgumentException.class, () -> HhSessionCookies.parse("вот мои куки"));
        assertThrows(IllegalArgumentException.class, () -> HhSessionCookies.parse("привет"));
    }

    /** Этот объект попадает в логи и исключения как любой другой. Значений там быть не должно. */
    @Test
    void toStringShowsNamesOnly() {
        String text = HhSessionCookies.parse("hhtoken=SECRET; _xsrf=ALSOSECRET").toString();

        assertEquals("HhSessionCookies[hhtoken, _xsrf]", text);
        assertFalse(text.contains("SECRET"));
    }
}
