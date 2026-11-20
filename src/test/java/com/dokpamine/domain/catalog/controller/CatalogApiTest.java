package com.dokpamine.domain.catalog.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

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
    @DisplayName("FR-01 YES24 분야를 앱 장르로 우선 사용하고 비어 있는 쪽수를 채운다")
    void yes24GenreAndPages() throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("nlk", isbn)).thenReturn("""
                {"docs":[{"EA_ISBN":"9788937460449","TITLE":"도서","KDC":"325.04"}]}
                """);
        when(fetcher.fetch("yes24", isbn)).thenReturn(yes24(isbn, "국내도서-자기계발", 736));

        var book = request(get("/api/catalog/isbn/" + isbn), user(), null, 200);
        assertEquals("자기계발", book.path("genre").asString());
        assertEquals(736, book.path("totalPages").asInt());
        assertEquals("nlk", book.path("source").asString());
    }

    @Test
    @DisplayName("FR-01 기존 조회처의 쪽수가 있으면 YES24 쪽수로 바꾸지 않는다")
    void yes24PagesFillOnlyBlank() throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("nlk", isbn)).thenReturn("""
                {"docs":[{"EA_ISBN":"9788937460449","TITLE":"도서","PAGE":"312 p.","KDC":"813.7"}]}
                """);
        when(fetcher.fetch("yes24", isbn)).thenReturn(yes24(isbn, "국내도서-소설/시/희곡", 239));

        var book = request(get("/api/catalog/isbn/" + isbn), user(), null, 200);
        assertEquals(312, book.path("totalPages").asInt());
        assertEquals("소설/시", book.path("genre").asString());
    }

    @Test
    @DisplayName("FR-01 YES24 소설/시/희곡 분야의 시집은 소설/시로 정한다")
    void yes24NovelKeepsPoetry() throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("nlk", isbn)).thenReturn("""
                {"docs":[{"EA_ISBN":"9788937460449","TITLE":"시집","KDC":"811.6"}]}
                """);
        when(fetcher.fetch("yes24", isbn)).thenReturn(yes24(isbn, "국내도서-소설/시/희곡", 120));

        var book = request(get("/api/catalog/isbn/" + isbn), user(), null, 200);
        assertEquals("소설/시", book.path("genre").asString());
    }

    @Test
    @DisplayName("FR-01 YES24 분야가 기타이면 기존 조회처의 장르를 유지한다")
    void yes24OtherKeepsMetadataGenre() throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("nlk", isbn)).thenReturn("""
                {"docs":[{"EA_ISBN":"9788937460449","TITLE":"평전","KDC":"991.1"}]}
                """);
        when(fetcher.fetch("yes24", isbn)).thenReturn(yes24(isbn, "국내도서-인물", 400));

        var book = request(get("/api/catalog/isbn/" + isbn), user(), null, 200);
        assertEquals("인문/사회", book.path("genre").asString());
    }

    @Test
    @DisplayName("FR-01 YES24만 일치하면 YES24 분야로 장르를 채운다")
    void yes24OnlyGenre() throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("yes24", isbn)).thenReturn(yes24(isbn, "국내도서-IT 모바일", 584));

        var book = request(get("/api/catalog/isbn/" + isbn), user(), null, 200);
        assertEquals("과학/IT", book.path("genre").asString());
        assertEquals(584, book.path("totalPages").asInt());
        assertEquals("yes24", book.path("source").asString());
    }

    @ParameterizedTest(name = "{0}: YES24 {1}, KDC {2} → {3}")
    @CsvSource({
        "'데미안, 싯다르타',국내도서-소설/시/희곡,853,소설/시",
        "모순,국내도서-소설/시/희곡,813.62,소설/시",
        "쇼펜하우어 인생수업,국내도서-인문,165.47,인문/사회",
        "마음의 어휘력,국내도서-에세이,818,에세이",
        "큰별쌤 한국사,국내도서-수험서 자격증,911,인문/사회",
    })
    @DisplayName("FR-01 팀에서 비교한 도서는 YES24 분야를 우선하고 기타는 KDC로 보완한다")
    void teamComparedBooks(String title, String goodsSortNm, String kdc, String genre)
            throws Exception {
        String isbn = "9788937460449";
        when(fetcher.fetch("nlk", isbn)).thenReturn("""
                {"docs":[{"EA_ISBN":"%s","TITLE":"%s","KDC":"%s"}]}
                """.formatted(isbn, title, kdc));
        when(fetcher.fetch("yes24", isbn)).thenReturn(yes24(isbn, goodsSortNm, 300));

        var book = request(get("/api/catalog/isbn/" + isbn), user(), null, 200);
        assertEquals(genre, book.path("genre").asString());
    }

    private static String yes24(String isbn, String goodsSortNm, int pages) {
        return """
                {"success":true,"data":{"items":[{"isbn13":"%s","title":"도서","goodsType":"도서",
                "goodsSortNm":"%s","pages":%d,"cover":"https://image.yes24.com/goods/2/L",
                "link":"https://www.yes24.com/product/goods/2"}]}}
                """
                .formatted(isbn, goodsSortNm, pages);
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
    @DisplayName("FR-01 장르 목록은 YES24 대분야에 맞춘 아홉 장르다")
    void genres() throws Exception {
        var genres = request(get("/api/catalog/genres"), user(), null, 200);
        assertEquals(
                List.of("소설/시", "에세이", "인문/사회", "경제/경영", "자기계발", "과학/IT", "예술", "만화", "기타"),
                json.convertValue(genres, List.class));
    }
}
