package com.dokpamine.domain.catalog.service;

import static org.junit.jupiter.api.Assertions.*;

import com.dokpamine.domain.catalog.dto.response.CatalogBook;
import com.dokpamine.domain.catalog.util.Isbn;
import com.dokpamine.global.apiPayload.exception.ApiException;
import com.dokpamine.global.config.CatalogProperties;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@ExtendWith(OutputCaptureExtension.class)
class BookLookupServiceTest {
    private static final String ISBN = "9788937460449";
    private final JsonMapper json = JsonMapper.builder().build();
    private final CatalogProperties configured =
            new CatalogProperties("nlk", "kakao", "google", null);

    private BookLookupService service(Map<String, String> responses) {
        return new BookLookupService(configured, json, (source, isbn) -> responses.get(source));
    }

    private String nlk(String pages) {
        return json.writeValueAsString(
                Map.of(
                        "docs",
                        List.of(
                                Map.of(
                                        "EA_ISBN",
                                        ISBN,
                                        "TITLE",
                                        "국내 도서",
                                        "AUTHOR",
                                        "지은이: 홍길동, 김철수 ; 옮긴이: 번역자",
                                        "PUBLISHER",
                                        "출판사",
                                        "PAGE",
                                        pages,
                                        "KDC",
                                        "813.7"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"312 p.", "312p", "xii, 312 p.", "12, 312 p."})
    @DisplayName("FR-01 국립중앙도서관에서 본문 쪽수와 첫 저자 및 장르를 읽는다")
    void nlkPages(String pages) {
        CatalogBook book = service(Map.of("nlk", nlk(pages))).lookup(ISBN);
        assertNotNull(book);
        assertEquals(312, book.totalPages());
        assertEquals("홍길동", book.author());
        assertEquals("소설", book.genre());
        assertEquals("nlk", book.source());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "xii p.", "0 p.", "-1 p.", "999999999999 p."})
    @DisplayName("FR-01 양수가 없는 쪽수와 정수 범위를 넘는 쪽수는 비워 둔다")
    void missingPages(String pages) {
        assertNull(service(Map.of("nlk", nlk(pages))).lookup(ISBN).totalPages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"8937460440", " 9788937460449", "8937460440 ", "89-374-6044-0"})
    @DisplayName("FR-01 카카오 ISBN10과 ISBN13을 정규화하여 요청한 책만 채택한다")
    void kakaoMatches(String responseIsbn) {
        String body =
                json.writeValueAsString(
                        Map.of(
                                "documents",
                                List.of(
                                        Map.of(
                                                "isbn",
                                                responseIsbn,
                                                "title",
                                                "카카오 도서",
                                                "authors",
                                                List.of("저자", "다른 저자"),
                                                "thumbnail",
                                                "http://example.com/cover.jpg"))));
        CatalogBook book = service(Map.of("kakao", body)).lookup(ISBN);
        assertEquals("카카오 도서", book.title());
        assertEquals("저자", book.author());
        assertEquals("https://example.com/cover.jpg", book.coverUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9788996991342", "8996991341", "97889374604490", ""})
    @DisplayName("FR-01 다른 ISBN의 응답은 채택하지 않는다")
    void mismatchedIsbn(String responseIsbn) {
        String body =
                json.writeValueAsString(
                        Map.of(
                                "documents",
                                List.of(Map.of("isbn", responseIsbn, "title", "다른 책"))));
        assertNull(service(Map.of("kakao", body)).lookup(ISBN));
    }

    @Test
    @DisplayName("FR-01 조회처 우선순위에 따라 빈 칸만 채우고 첫 출처를 유지한다")
    void merge() {
        List<String> calls = new ArrayList<>();
        Map<String, String> responses =
                Map.of(
                        "nlk", nlk("312 p."),
                        "kakao",
                                """
                                {"documents":[{"isbn":"8937460440 9788937460449","title":"카카오 도서","thumbnail":"http://example.com/kakao.jpg"}]}
                                """,
                        "google",
                                """
                                {"items":[{"volumeInfo":{"title":"Google 도서","pageCount":400,"categories":["Science"]}}]}
                                """);
        CatalogBook book =
                new BookLookupService(
                                configured,
                                json,
                                (source, isbn) -> {
                                    calls.add(source);
                                    assertEquals(ISBN, isbn);
                                    return responses.get(source);
                                })
                        .lookup("8937460440");
        assertEquals(List.of("nlk", "kakao", "google", "openlibrary"), calls);
        assertEquals("국내 도서", book.title());
        assertEquals(312, book.totalPages());
        assertEquals("소설", book.genre());
        assertEquals("https://example.com/kakao.jpg", book.coverUrl());
        assertEquals("nlk", book.source());
    }

    @Test
    @DisplayName("FR-01 파싱 오류는 건너뛰고 Google 및 Open Library 정보를 읽는다")
    void fallback() {
        CatalogBook google =
                service(
                                Map.of(
                                        "nlk",
                                        "not-json",
                                        "google",
                                        """
                                        {"items":[{"volumeInfo":{"title":"Google 도서","subtitle":"부제","pageCount":400,"categories":["Science"],"imageLinks":{"thumbnail":"http://example.com/google.jpg"}}}]}
                                        """))
                        .lookup(ISBN);
        assertEquals("Google 도서: 부제", google.title());
        assertEquals("과학", google.genre());
        CatalogBook open =
                service(
                                Map.of(
                                        "openlibrary",
                                        """
                                        {"ISBN:9788937460449":{"title":"Open Library 도서","authors":[{"name":"저자"}],"publishers":[{"name":"출판사"}],"number_of_pages":500,"subjects":[{"name":"Other"},{"name":"History"}],"cover":{"medium":"http://example.com/open.jpg"}}}
                                        """))
                        .lookup(ISBN);
        assertEquals("Open Library 도서", open.title());
        assertEquals("저자", open.author());
        assertEquals("출판사", open.publisher());
        assertEquals(500, open.totalPages());
        assertEquals("역사", open.genre());
        assertEquals("https://example.com/open.jpg", open.coverUrl());
    }

    @Test
    @DisplayName("FR-01 키가 없으면 Open Library만 호출하고 잘못된 ISBN은 호출 전에 거절한다")
    void absentKeysAndValidation() {
        List<String> calls = new ArrayList<>();
        BookLookupService service =
                new BookLookupService(
                        new CatalogProperties(null, null, null, null),
                        json,
                        (source, isbn) -> {
                            calls.add(source);
                            return null;
                        });
        assertNull(service.lookup(ISBN));
        assertEquals(List.of("openlibrary"), calls);
        calls.clear();
        assertThrows(ApiException.class, () -> service.lookup("invalid"));
        assertTrue(calls.isEmpty());
        assertEquals(ISBN, Isbn.normalize("978-89-374-6044-9"));
        assertEquals(ISBN, Isbn.normalize("8937460440"));
        assertNull(Isbn.normalize("9788937460440"));
    }

    @Test
    @DisplayName("FR-01 외부 조회 오류에 포함된 인증키를 로그에 남기지 않는다")
    void safeLogs(CapturedOutput output) {
        BookLookupService service =
                new BookLookupService(
                        configured,
                        json,
                        (source, isbn) -> {
                            throw new IllegalStateException("cert_key=test-secret");
                        });
        assertNull(service.lookup(ISBN));
        assertFalse(output.getAll().contains("test-secret"));
    }
}
