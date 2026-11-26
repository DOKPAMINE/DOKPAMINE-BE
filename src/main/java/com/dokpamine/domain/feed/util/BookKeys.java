package com.dokpamine.domain.feed.util;

import com.dokpamine.domain.catalog.util.Isbn;
import com.dokpamine.global.util.TextValues;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** 서로 다른 사람이 등록한 같은 책을 한 묶음으로 보기 위한 키를 만든다(FR-16). */
public final class BookKeys {
    // Java와 DB 정규식이 같은 공백 문자를 제거하도록 각 문자를 명시한다.
    public static final Pattern WHITESPACE =
            Pattern.compile(
                    "[ \t\n\u000b\f\r\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005"
                            + "\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000]+");
    private static final String ISBN_PREFIX = "isbn:";

    private BookKeys() {}

    public static String of(String rawIsbn, String title, String author) {
        String trimmed = TextValues.trim(rawIsbn);
        if (trimmed != null && !trimmed.isEmpty()) {
            return ISBN_PREFIX + Objects.requireNonNullElse(Isbn.normalize(trimmed), trimmed);
        }
        String text = normalize(title) + "\n" + normalize(author);
        return "text:"
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** ISBN으로 묶은 키면 정규화한 ISBN을, 제목·저자로 묶은 키면 null을 돌려준다. */
    public static String isbn(String key) {
        return key.startsWith(ISBN_PREFIX) ? key.substring(ISBN_PREFIX.length()) : null;
    }

    private static String normalize(String value) {
        return TextValues.trim(WHITESPACE.matcher(value).replaceAll(" ")).toLowerCase(Locale.ROOT);
    }
}
