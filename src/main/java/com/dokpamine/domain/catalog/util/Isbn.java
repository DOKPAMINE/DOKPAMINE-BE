package com.dokpamine.domain.catalog.util;

import java.util.Locale;

public final class Isbn {
    private Isbn() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String value = raw.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (value.matches("[0-9]{13}") && check13(value.substring(0, 12)) == value.charAt(12) - '0')
            return value;
        if (value.matches("[0-9]{9}[0-9X]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++)
                sum += (10 - i) * (value.charAt(i) == 'X' ? 10 : value.charAt(i) - '0');
            if (sum % 11 == 0) {
                String first12 = "978" + value.substring(0, 9);
                return first12 + check13(first12);
            }
        }
        return null;
    }

    private static int check13(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (first12.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - sum % 10) % 10;
    }
}
