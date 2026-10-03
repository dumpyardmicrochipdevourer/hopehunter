package com.antonk404.hhbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шифрует куки hh перед записью в базу.
 *
 * <p>AES-GCM, а не просто AES: дамп базы без ключа бесполезен, а подменённая в базе строка не
 * расшифруется вовсе, вместо того чтобы превратиться в чужую сессию.
 */
@Component
public class CookieCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CookieCipher(@Value("${hh.cookie-key}") String keyBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(keyBase64.strip());
        } catch (IllegalArgumentException e) {
            // Без cause: его сообщение цитирует кусок самого ключа.
            throw new IllegalStateException("HH_COOKIE_KEY is not base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("HH_COOKIE_KEY must be 32 bytes in base64, see .env.example");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cookie encryption failed", e);
        }
    }

    /** @throws IllegalStateException если строка испорчена или зашифрована другим ключом */
    public String decrypt(String stored) {
        try {
            byte[] bytes = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("cookie decryption failed - wrong key or corrupted row");
        }
    }
}
