package com.dokpamine.domain.catalog.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;

class CatalogApiTest extends ApiTestSupport {
    @Test
    @DisplayName("FR-01 ISBN 조회는 기존 JSON 계약으로 쪽수와 표지를 병합한다")
    void lookup() throws Exception {
        when(fetcher.fetch("nlk", "9788937460449"))
                .thenReturn(
                        """
                        {"docs":[{"EA_ISBN":"9788937460449","TITLE":"도서","PAGE":"312 p.","KDC":"813.7"}]}
                        """);
        when(fetcher.fetch("kakao", "9788937460449"))
                .thenReturn(
                        """
                        {"documents":[{"isbn":"8937460440","thumbnail":"http://example.com/cover.jpg"}]}
                        """);
        var book = request(get("/api/catalog/isbn/8937460440"), user(), null, 200);
        assertEquals("9788937460449", book.path("isbn").asString());
        assertEquals(312, book.path("totalPages").asInt());
        assertEquals("https://example.com/cover.jpg", book.path("coverUrl").asString());
    }

    @Test
    @DisplayName("FR-01 도서 정보 없음은 404이고 잘못된 ISBN은 400이다")
    void errors() throws Exception {
        var user = user();
        assertEquals(
                "NOT_FOUND",
                request(get("/api/catalog/isbn/9788937460449"), user, null, 404)
                        .path("code")
                        .asString());
        reset(fetcher);
        assertEquals(
                "BAD_REQUEST",
                request(get("/api/catalog/isbn/invalid"), user, null, 400).path("code").asString());
        verifyNoInteractions(fetcher);
    }

    @Test
    @DisplayName("FR-01 장르 목록은 기존 열두 장르를 유지한다")
    void genres() throws Exception {
        var genres = request(get("/api/catalog/genres"), user(), null, 200);
        assertEquals(12, genres.size());
        assertEquals("소설", genres.path(0).asString());
        assertEquals("기타", genres.path(11).asString());
    }
}
