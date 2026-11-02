package com.dokpamine.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Assertions.assertNull

class GenresTest {
    @Test
    fun `FR-01 Outer Party를 예술로 오인하지 않는다`() {
        assertNotEquals("예술", Genres.fromCategory("Outer Party"))
    }

    @ParameterizedTest(name = "{0} → 기타")
    @ValueSource(strings = [
        "Heart of Darkness", "Party politics", "Start", "Antisocial", "Politicality", "Prehistory",
        "Unmusical", "Businessman", "Uneconomics", "Metaphilosophy", "Psychology2", "Misfit",
        "Fictional", "Essayist", "Comical", "Graphic Novelist", "Self-Helpful", "2art", "Xyz",
    ])
    fun `FR-01 영어 키워드가 다른 단어의 일부이면 장르로 매핑하지 않는다`(category: String) {
        assertEquals("기타", Genres.fromCategory(category))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "Art,예술", "Arts & Photography,예술", "Music / General,예술", "(aRt),예술",
        "SF,SF", "Fiction / Science Fiction / General,SF", "Comics & Graphic Novels,만화",
        "Comic,만화", "Graphic Novel,만화", "Graphic Novels,만화", "Poetry,시/에세이",
        "Essay,시/에세이", "Essays,시/에세이", "Fiction,소설", "History,역사",
        "Business & Economics,경제/경영", "Economics,경제/경영", "Self-Help,자기계발",
        "Science,과학", "Political Science,과학", "Political,사회/정치", "Social,사회/정치",
        "Socialism,사회/정치", "Philosophy,인문", "Psychology,인문",
        "Science Fictional,과학", "Science Fiction2,과학", "Pseudoscience Fiction,소설",
    )
    fun `FR-01 영어 단어와 복수형 및 여러 단어 키워드를 기존 우선순위로 매핑한다`(category: String, genre: String) {
        assertEquals(genre, Genres.fromCategory(category))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "한국소설,소설", "과학소설,SF", "에세이,시/에세이", "에세이를,시/에세이", "시/한국시,시/에세이",
        "만화를,만화", "한국역사,역사", "경제학,경제/경영", "경영학,경제/경영", "자기계발서,자기계발",
        "과학의 역사,역사", "과학책,과학", "사회학,사회/정치", "정치학,사회/정치", "예술론,예술",
        "인문학,인문", "철학사,인문",
    )
    fun `FR-01 한국어 키워드는 포함 여부와 기존 우선순위로 매핑한다`(category: String, genre: String) {
        assertEquals(genre, Genres.fromCategory(category))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" "])
    fun `FR-01 빈 카테고리는 장르를 비워 둔다`(category: String?) {
        assertNull(Genres.fromCategory(category))
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "813.7,소설", "811,시/에세이", "814,시/에세이", "812,인문", "800,인문", "822,인문", "843,소설",
        "911,역사", "320,경제/경영", "325,경제/경영", "329,경제/경영", "330,사회/정치", "340,사회/정치",
        "657,만화", "657.1,만화", "600,예술", "410,과학", "510,과학", "000,기타", "100,인문",
        "200,인문", "700,인문", "199,자기계발", "199.1,자기계발", "816,시/에세이", "818,인문", "863.6,소설",
    )
    fun `FR-01 KDC 분류 경계를 앱 장르에 맞게 매핑한다`(kdc: String, genre: String) {
        assertEquals(genre, Genres.fromKdc(kdc))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" ", "8", "81", "813.", "813a", "813.7a", "-100", "1000", "８１３", "813;811"])
    fun `FR-01 빈 값과 형식 오류는 뒤 조회처의 장르로 채울 수 있다`(kdc: String?) {
        assertNull(Genres.fromKdc(kdc))
    }

    @ParameterizedTest
    @ValueSource(strings = ["892.83", "890", "879.3", "839.1", "829"])
    fun `FR-01 셋째 자리가 언어인 문학 분류는 형식을 추측하지 않고 비워 둔다`(kdc: String) {
        assertNull(Genres.fromKdc(kdc))
    }
}
