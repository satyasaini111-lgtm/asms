package com.asms.user.karate;

import java.security.SecureRandom;
import java.util.stream.IntStream;

public class PasswordGenerator {

    private static final String CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length) {
        return IntStream.range(0, length)
                .mapToObj(i -> String.valueOf(CHARS.charAt(RANDOM.nextInt(CHARS.length()))))
                .reduce("", String::concat);
    }
}
