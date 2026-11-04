package com.dokpamine.global.util;

/** Kotlin의 trim/isNullOrBlank와 동일하게 Unicode 공백도 처리한다. */
public final class TextValues {
    private TextValues() {}

    public static boolean isBlank(String value) {
        return value == null || value.codePoints().allMatch(TextValues::isWhitespace);
    }

    public static String trim(String value) {
        if (value == null) return null;
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (start < end && isWhitespace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : trim(value);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
