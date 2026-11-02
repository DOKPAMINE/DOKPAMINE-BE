package com.dokpamine.catalog

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(properties = [
    "dokpamine.catalog.nlk-cert-key=test-nlk",
    "dokpamine.catalog.kakao-rest-api-key=test-kakao",
    "dokpamine.catalog.google-books-key=",
])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class CatalogApiTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var fetcher: CatalogFetcher

    private val isbn = "9788937460449"

    @BeforeEach
    fun fakeResponses() {
        // HTTP 구현을 통째로 대체해 어떤 테스트 환경 변수에서도 외부 API를 호출하지 않는다.
        `when`(fetcher.fetch(anyString(), anyString())).thenReturn("{}")
    }

    @Test
    fun `FR-01 ISBN 조회는 국립중앙도서관 쪽수와 카카오 표지를 기존 응답 계약으로 돌려준다`() {
        `when`(fetcher.fetch("nlk", isbn)).thenReturn("""{"TOTAL_COUNT":"1","docs":[{
            "EA_ISBN":"$isbn","TITLE":"국내 도서","AUTHOR":"지은이: 홍길동 ; 옮긴이: 번역자",
            "PUBLISHER":"출판사","PAGE":"312 p.","KDC":"813.7","TITLE_URL":""
        }]}""")
        `when`(fetcher.fetch("kakao", isbn)).thenReturn("""{"documents":[{
            "isbn":"8937460440 $isbn","thumbnail":"http://example.com/cover.jpg"
        }]}""")

        mvc.get("/api/catalog/isbn/8937460440").andExpect {
            status { isOk() }
            jsonPath("$.isbn") { value(isbn) }
            jsonPath("$.title") { value("국내 도서") }
            jsonPath("$.author") { value("홍길동") }
            jsonPath("$.publisher") { value("출판사") }
            jsonPath("$.totalPages") { value(312) }
            jsonPath("$.genre") { value("소설") }
            jsonPath("$.coverUrl") { value("https://example.com/cover.jpg") }
            jsonPath("$.source") { value("nlk") }
            jsonPath("$.length()") { value(8) }
        }
    }

    @Test
    fun `FR-01 도서 정보가 없으면 기존 404 오류를 돌려준다`() {
        mvc.get("/api/catalog/isbn/$isbn").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `FR-01 잘못된 ISBN은 조회 없이 기존 400 오류를 돌려준다`() {
        mvc.get("/api/catalog/isbn/invalid").andExpect {
            status { isBadRequest() }
        }
        verifyNoInteractions(fetcher)
    }

    @Test
    fun `FR-01 앱 장르 목록은 기존 열두 장르를 유지한다`() {
        mvc.get("/api/catalog/genres").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(12) }
            jsonPath("$[0]") { value("소설") }
            jsonPath("$[1]") { value("SF") }
        }
        verifyNoInteractions(fetcher)
    }
}
