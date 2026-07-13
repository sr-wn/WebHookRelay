package dev.webhookrelay.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates cryptographically-random, URL-safe slugs (nanoid-style).
 * NOT sequential — prevents endpoint enumeration attacks.
 */
@Component
public class SlugGenerator {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-".toCharArray();
    private static final int DEFAULT_LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
