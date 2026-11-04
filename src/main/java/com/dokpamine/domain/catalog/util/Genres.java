package com.dokpamine.domain.catalog.util;

import com.dokpamine.global.util.TextValues;

import java.util.*;
import java.util.regex.Pattern;

public final class Genres {
    public static final List<String> ALL =
            List.of(
                    "소설", "SF", "시/에세이", "인문", "역사", "사회/정치", "경제/경영", "자기계발", "과학", "예술", "만화",
                    "기타");

    private Genres() {}

    public static String fromKdc(String raw) {
        String value = TextValues.trim(raw);
        if (value == null || !value.matches("[0-9]{3}(\\.[0-9]+)?")) return null;
        int code = Integer.parseInt(value.substring(0, 3));
        if (code == 199) return "자기계발";
        if (code == 657) return "만화";
        if (code >= 320 && code <= 329) return "경제/경영";
        // FR-01: 89x와 8x9는 셋째 자리가 언어이므로 문학 형식을 추측하지 않는다.
        if (code >= 890 && code <= 899 || code >= 800 && code <= 899 && code % 10 == 9) return null;
        if (code >= 800 && code <= 899)
            return switch (code % 10) {
                case 3 -> "소설";
                case 1, 4, 6 -> "시/에세이";
                default -> "인문";
            };
        if (code >= 900) return "역사";
        if (code >= 300 && code <= 399) return "사회/정치";
        if (code >= 100 && code <= 299 || code >= 700 && code <= 799) return "인문";
        if (code >= 400 && code <= 599) return "과학";
        if (code >= 600 && code <= 699) return "예술";
        return "기타";
    }

    public static String fromCategory(String category) {
        if (TextValues.isBlank(category)) return null;
        String c = category.toLowerCase(Locale.ROOT);
        if (english(c, "sf") || english(c, "science[ -]fiction") || c.contains("과학소설")) return "SF";
        if (c.contains("만화") || english(c, "comics?") || english(c, "graphic novels?")) return "만화";
        if (c.contains("에세이") || c.contains("시/") || english(c, "poetry") || english(c, "essays?"))
            return "시/에세이";
        if (c.contains("소설") || english(c, "fiction")) return "소설";
        if (c.contains("역사") || english(c, "history")) return "역사";
        if (c.contains("경제")
                || c.contains("경영")
                || english(c, "business")
                || english(c, "economics")) return "경제/경영";
        if (c.contains("자기계발") || english(c, "self[ -]help")) return "자기계발";
        if (c.contains("과학") || english(c, "sciences?")) return "과학";
        if (c.contains("사회")
                || c.contains("정치")
                || english(c, "political")
                || english(c, "social(?:ism|ists?)?")) return "사회/정치";
        if (c.contains("예술") || english(c, "arts?|artists?") || english(c, "music(?:als?|ians?)?"))
            return "예술";
        if (c.contains("인문")
                || c.contains("철학")
                || english(c, "philosophy")
                || english(c, "psychology")) return "인문";
        return "기타";
    }

    private static boolean english(String category, String pattern) {
        return Pattern.compile("(?<![a-z0-9])(?:" + pattern + ")(?![a-z0-9])")
                .matcher(category)
                .find();
    }
}
