package com.dokpamine.domain.catalog.util;

import com.dokpamine.global.util.TextValues;

import java.util.*;
import java.util.regex.Pattern;

public final class Genres {
    public static final String LITERATURE = "소설/시";
    public static final String ESSAY = "에세이";
    public static final String HUMANITIES = "인문/사회";
    public static final String ECONOMY = "경제/경영";
    public static final String SELF_HELP = "자기계발";
    public static final String SCIENCE = "과학/IT";
    public static final String ART = "예술";
    public static final String COMICS = "만화";
    public static final String OTHER = "기타";

    // FR-01: 선택지가 많으면 등록과 통계가 번거로워 YES24 대분야에 맞춰 아홉 개로 묶는다.
    public static final List<String> ALL =
            List.of(LITERATURE, ESSAY, HUMANITIES, ECONOMY, SELF_HELP, SCIENCE, ART, COMICS, OTHER);

    // YES24 goodsSortNm의 분야명(국내도서·외국도서·eBook 2단계)을 앱 장르로 옮긴다.
    private static final Map<String, String> YES24 =
            Map.ofEntries(
                    Map.entry("소설/시/희곡", LITERATURE),
                    Map.entry("문학 소설", LITERATURE),
                    Map.entry("소설", LITERATURE),
                    Map.entry("로맨스", LITERATURE),
                    Map.entry("판타지/무협", LITERATURE),
                    Map.entry("라이트노벨", LITERATURE),
                    Map.entry("BL", LITERATURE),
                    Map.entry("에세이", ESSAY),
                    // eBook은 에세이와 시를 묶어 주므로 더 흔한 에세이로 둔다.
                    Map.entry("에세이 시", ESSAY),
                    Map.entry("인문", HUMANITIES),
                    Map.entry("인문 사회", HUMANITIES),
                    Map.entry("종교", HUMANITIES),
                    Map.entry("역사", HUMANITIES),
                    Map.entry("사회 정치", HUMANITIES),
                    Map.entry("경제 경영", ECONOMY),
                    Map.entry("자기계발", SELF_HELP),
                    Map.entry("자연과학", SCIENCE),
                    Map.entry("IT 모바일", SCIENCE),
                    Map.entry("컴퓨터", SCIENCE),
                    Map.entry("예술", ART),
                    Map.entry("예술 대중문화", ART),
                    Map.entry("만화/라이트노벨", COMICS),
                    Map.entry("만화", COMICS),
                    Map.entry("BL만화", COMICS));

    private Genres() {}

    /**
     * YES24 관리 분류("국내도서-소설/시/희곡")를 앱 장르로 바꾼다. 대분류만 있으면 장르를 알 수 없으므로 null이고, 표에 없는
     * 분야(여행, 참고서 등)는 기타다(FR-01).
     */
    public static String fromYes24(String goodsSortNm) {
        String value = TextValues.trim(goodsSortNm);
        int separator = value == null ? -1 : value.indexOf('-');
        if (separator < 0) return null;
        String field = TextValues.trimToNull(value.substring(separator + 1));
        if (field == null) return null;
        return YES24.getOrDefault(field, OTHER);
    }

    public static String fromKdc(String raw) {
        String value = TextValues.trim(raw);
        if (value == null || !value.matches("[0-9]{3}(\\.[0-9]+)?")) return null;
        int code = Integer.parseInt(value.substring(0, 3));
        if (code == 199) return SELF_HELP;
        if (code == 657) return COMICS;
        if (code == 4 || code == 5) return SCIENCE;
        if (code >= 320 && code <= 329) return ECONOMY;
        // FR-01: 89x와 8x9는 셋째 자리가 언어이므로 문학 형식을 추측하지 않는다.
        if (code >= 890 && code <= 899 || code >= 800 && code <= 899 && code % 10 == 9) return null;
        if (code >= 800 && code <= 899)
            return switch (code % 10) {
                case 1, 2, 3 -> LITERATURE;
                case 4, 6 -> ESSAY;
                default -> HUMANITIES;
            };
        if (code >= 100 && code <= 399 || code >= 700 && code <= 799 || code >= 900)
            return HUMANITIES;
        if (code >= 400 && code <= 599) return SCIENCE;
        if (code >= 600 && code <= 699) return ART;
        return OTHER;
    }

    public static String fromCategory(String category) {
        if (TextValues.isBlank(category)) return null;
        String c = category.toLowerCase(Locale.ROOT);
        if (english(c, "sf") || english(c, "science[ -]fiction") || c.contains("과학소설"))
            return LITERATURE;
        if (c.contains("만화") || english(c, "comics?") || english(c, "graphic novels?")) return COMICS;
        if (c.contains("에세이") || english(c, "essays?")) return ESSAY;
        if (c.contains("시/") || english(c, "poetry")) return LITERATURE;
        if (c.contains("소설") || english(c, "fiction")) return LITERATURE;
        if (c.contains("역사") || english(c, "history")) return HUMANITIES;
        if (c.contains("경제")
                || c.contains("경영")
                || english(c, "business")
                || english(c, "economics")) return ECONOMY;
        if (c.contains("자기계발") || english(c, "self[ -]help")) return SELF_HELP;
        if (c.contains("과학") || english(c, "sciences?") || english(c, "computers?")) return SCIENCE;
        if (c.contains("사회")
                || c.contains("정치")
                || english(c, "political")
                || english(c, "social(?:ism|ists?)?")) return HUMANITIES;
        if (c.contains("예술") || english(c, "arts?|artists?") || english(c, "music(?:als?|ians?)?"))
            return ART;
        if (c.contains("인문")
                || c.contains("철학")
                || english(c, "philosophy")
                || english(c, "psychology")) return HUMANITIES;
        return OTHER;
    }

    private static boolean english(String category, String pattern) {
        return Pattern.compile("(?<![a-z0-9])(?:" + pattern + ")(?![a-z0-9])")
                .matcher(category)
                .find();
    }
}
