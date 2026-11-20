package com.dokpamine.domain.catalog.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class GenresTest {
    @Test
    @DisplayName("FR-01 Outer Party를 예술로 오인하지 않는다")
    void case33() {
        assertNotEquals("예술", Genres.fromCategory("Outer Party"));
    }

    @ParameterizedTest(name = "{0} → 기타")
    @ValueSource(
            strings = {
                "Heart of Darkness",
                "Party politics",
                "Start",
                "Antisocial",
                "Politicality",
                "Prehistory",
                "Unmusical",
                "Businessman",
                "Uneconomics",
                "Metaphilosophy",
                "Psychology2",
                "Misfit",
                "Fictional",
                "Essayist",
                "Comical",
                "Graphic Novelist",
                "Self-Helpful",
                "2art",
                "Xyz",
            })
    @DisplayName("FR-01 영어 키워드가 다른 단어의 일부이면 장르로 매핑하지 않는다")
    void case519(String category) {
        assertEquals("기타", Genres.fromCategory(category));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "Art,예술",
        "Arts & Photography,예술",
        "Music / General,예술",
        "(aRt),예술",
        "SF,소설/시",
        "Fiction / Science Fiction / General,소설/시",
        "Comics & Graphic Novels,만화",
        "Comic,만화",
        "Graphic Novel,만화",
        "Graphic Novels,만화",
        "Poetry,소설/시",
        "Essay,에세이",
        "Essays,에세이",
        "Fiction,소설/시",
        "History,인문/사회",
        "Business & Economics,경제/경영",
        "Economics,경제/경영",
        "Self-Help,자기계발",
        "Science,과학/IT",
        "Political Science,과학/IT",
        "Political,인문/사회",
        "Social,인문/사회",
        "Socialism,인문/사회",
        "Philosophy,인문/사회",
        "Psychology,인문/사회",
        "Science Fictional,과학/IT",
        "Science Fiction2,과학/IT",
        "Pseudoscience Fiction,소설/시",
    })
    @DisplayName("FR-01 영어 단어와 복수형 및 여러 단어 키워드를 기존 우선순위로 매핑한다")
    void case1328(String category, String genre) {
        assertEquals(genre, Genres.fromCategory(category));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "Sciences,과학/IT",
        "Life sciences,과학/IT",
        "Artists,예술",
        "Musicians,예술",
        "Musicals,예술",
        "Musical instruments,예술",
        "Socialists,인문/사회",
        "Science-Fiction,소설/시",
        "Self help,자기계발",
        "Artificial intelligence,기타",
        "Computers / Programming,과학/IT",
    })
    @DisplayName("FR-01 단어 첫머리가 같은 파생형과 하이픈 표기는 같은 장르로 매핑한다")
    void case1768(String category, String genre) {
        assertEquals(genre, Genres.fromCategory(category));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "한국소설,소설/시", "과학소설,소설/시", "에세이,에세이", "에세이를,에세이", "시/한국시,소설/시",
        "만화를,만화", "한국역사,인문/사회", "경제학,경제/경영", "경영학,경제/경영", "자기계발서,자기계발",
        "과학의 역사,인문/사회", "과학책,과학/IT", "사회학,인문/사회", "정치학,인문/사회", "예술론,예술",
        "인문학,인문/사회", "철학사,인문/사회",
    })
    @DisplayName("FR-01 한국어 키워드는 포함 여부와 기존 우선순위로 매핑한다")
    void case2222(String category, String genre) {
        assertEquals(genre, Genres.fromCategory(category));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("FR-01 빈 카테고리는 장르를 비워 둔다")
    void case2450(String category) {
        assertNull(Genres.fromCategory(category));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "813.7,소설/시",
        "811,소설/시",
        "814,에세이",
        "812,소설/시",
        "800,인문/사회",
        "822,소설/시",
        "843,소설/시",
        "911,인문/사회",
        "320,경제/경영",
        "325,경제/경영",
        "329,경제/경영",
        "330,인문/사회",
        "340,인문/사회",
        "657,만화",
        "657.1,만화",
        "600,예술",
        "410,과학/IT",
        "510,과학/IT",
        "000,기타",
        "004.73,과학/IT",
        "005,과학/IT",
        "100,인문/사회",
        "200,인문/사회",
        "700,인문/사회",
        "199,자기계발",
        "199.1,자기계발",
        "816,에세이",
        "818,인문/사회",
        "863.6,소설/시",
    })
    @DisplayName("FR-01 KDC 분류 경계를 앱 장르에 맞게 매핑한다")
    void case2965(String kdc, String genre) {
        assertEquals(genre, Genres.fromKdc(kdc));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {" ", "8", "81", "813.", "813a", "813.7a", "-100", "1000", "８１３", "813;811"})
    @DisplayName("FR-01 빈 값과 형식 오류는 뒤 조회처의 장르로 채울 수 있다")
    void case3244(String kdc) {
        assertNull(Genres.fromKdc(kdc));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "국내도서-소설/시/희곡,소설/시",
        "국내도서-에세이,에세이",
        "국내도서-인문,인문/사회",
        "국내도서-종교,인문/사회",
        "국내도서-역사,인문/사회",
        "국내도서-사회 정치,인문/사회",
        "국내도서-경제 경영,경제/경영",
        "국내도서-자기계발,자기계발",
        "국내도서-자연과학,과학/IT",
        "국내도서-IT 모바일,과학/IT",
        "국내도서-예술,예술",
        "국내도서-만화/라이트노벨,만화",
        "국내도서-여행,기타",
        "국내도서-인물,기타",
        "외국도서-문학 소설,소설/시",
        "외국도서-컴퓨터,과학/IT",
        "eBook-판타지/무협,소설/시",
        "eBook-에세이 시,에세이",
        "eBook-BL만화,만화",
        "' 국내도서- 자기계발 ',자기계발",
    })
    @DisplayName("FR-01 YES24 관리 분류의 분야명을 앱 장르로 매핑한다")
    void yes24(String goodsSortNm, String genre) {
        assertEquals(genre, Genres.fromYes24(goodsSortNm));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "국내도서", "도서", "국내도서-", "국내도서- "})
    @DisplayName("FR-01 YES24 분야명이 없으면 다른 조회처의 장르로 채울 수 있게 비워 둔다")
    void yes24Blank(String goodsSortNm) {
        assertNull(Genres.fromYes24(goodsSortNm));
    }

    @ParameterizedTest
    @ValueSource(strings = {"892.83", "890", "879.3", "839.1", "829"})
    @DisplayName("FR-01 셋째 자리가 언어인 문학 분류는 형식을 추측하지 않고 비워 둔다")
    void case3448(String kdc) {
        assertNull(Genres.fromKdc(kdc));
    }
}
