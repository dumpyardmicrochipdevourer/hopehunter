package com.antonk404.hhbot.service.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CookieCipherTest {

    private static String key(int fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void roundTrips() {
        CookieCipher cipher = new CookieCipher(key(1));

        assertEquals("hhtoken=abc; _xsrf=def", cipher.decrypt(cipher.encrypt("hhtoken=abc; _xsrf=def")));
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime() {
        CookieCipher cipher = new CookieCipher(key(1));

        assertNotEquals(cipher.encrypt("hhtoken=abc"), cipher.encrypt("hhtoken=abc"));
    }

    @Test
    void ciphertextDoesNotContainThePlainValue() {
        String encrypted = new CookieCipher(key(1)).encrypt("hhtoken=SECRETVALUE");

        assertFalse(new String(Base64.getDecoder().decode(encrypted)).contains("SECRETVALUE"));
    }

    @Test
    void wrongKeyFailsInsteadOfReturningGarbage() {
        String encrypted = new CookieCipher(key(1)).encrypt("hhtoken=abc");

        assertThrows(IllegalStateException.class, () -> new CookieCipher(key(2)).decrypt(encrypted));
    }

    @Test
    void rejectsKeyOfWrongLength() {
        assertThrows(IllegalStateException.class,
                () -> new CookieCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
