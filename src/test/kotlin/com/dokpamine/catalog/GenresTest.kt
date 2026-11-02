package com.dokpamine.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Assertions.assertNull

class GenresTest {
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "813.7,소설", "811,시/에세이", "814,시/에세이", "812,인문", "800,인문", "829,인문", "843,소설",
        "911,역사", "320,경제/경영", "325,경제/경영", "329,경제/경영", "330,사회/정치", "340,사회/정치",
        "657,만화", "657.1,만화", "600,예술", "410,과학", "510,과학", "000,기타", "100,인문",
        "200,인문", "700,인문", "199,자기계발", "199.1,자기계발",
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
}
