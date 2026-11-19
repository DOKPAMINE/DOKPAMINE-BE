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
    @DisplayName("FR-01 기존 정보가 완전해도 같은 ISBN의 YES24 앞표지와 책등을 반환한다")
    void yes24Images() throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("nlk", isbn)).thenReturn("""
                {"docs":[{"EA_ISBN":"9788937460449","TITLE":"도서","AUTHOR":"저자",
                "PUBLISHER":"출판사","PAGE":"312 p.","KDC":"813.7","TITLE_URL":"https://example.com/cover.jpg"}]}
                """);
        when(fetcher.fetch("yes24", isbn)).thenReturn("""
                {"success":true,"data":{"items":[
                {"isbn13":"9788996991342","link":"https://www.yes24.com/product/goods/1"},
                {"isbn13":"9788937460449","title":"YES24 제목","cover":"https://image.yes24.com/goods/2/L",
                "sideCover":"https://image.yes24.com/goods/2/SIDE/L","link":"https://www.yes24.com/product/goods/2"}]}}
                """);

        var book = request(get("/api/catalog/isbn/8937460440"), user(), null, 200);
        assertEquals("도서", book.path("title").asString());
        assertEquals(312, book.path("totalPages").asInt());
        assertEquals("nlk", book.path("source").asString());
        assertEquals("https://image.yes24.com/goods/2/L", book.path("coverUrl").asString());
        assertEquals("https://image.yes24.com/goods/2/SIDE/L", book.path("sideCoverUrl").asString());
        assertEquals("https://www.yes24.com/product/goods/2", book.path("yes24ProductUrl").asString());
        verify(fetcher).fetch("yes24", isbn);
        verify(fetcher, never()).fetch("kakao", isbn);
    }

    @Test
    @DisplayName("FR-01 YES24만 일치하는 책을 찾으면 그 상품으로 ISBN 조회를 성공시킨다")
    void yes24Only() throws Exception {
        when(fetcher.fetch("yes24", "9788937460449")).thenReturn("""
                {"success":true,"data":{"items":[{"isbn13":"9788937460449","title":"도서",
                "cover":"https://image.yes24.com/goods/2/L","link":"https://www.yes24.com/product/goods/2"}]}}
                """);

        var book = request(get("/api/catalog/isbn/9788937460449"), user(), null, 200);
        assertEquals("도서", book.path("title").asString());
        assertEquals("yes24", book.path("source").asString());
        assertTrue(book.path("sideCoverUrl").isNull());
    }

    @Test
    @DisplayName("FR-01 YES24 조회가 실패해도 기존 표지로 응답한다")
    void yes24Failure() throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("yes24", isbn)).thenThrow(new IllegalStateException("외부 조회 실패"));
        when(fetcher.fetch("kakao", isbn)).thenReturn("""
                {"documents":[{"isbn":"9788937460449","thumbnail":"https://example.com/cover.jpg"}]}
                """);

        var book = request(get("/api/catalog/isbn/" + isbn), user(), null, 200);
        assertEquals("https://example.com/cover.jpg", book.path("coverUrl").asString());
        assertTrue(book.path("sideCoverUrl").isNull());
        assertTrue(book.path("yes24ProductUrl").isNull());
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
