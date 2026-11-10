package com.dokpamine.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.domain.feed.entity.*;
import com.dokpamine.domain.feed.repository.*;
import com.dokpamine.domain.feed.util.NativePublishedAt;
import com.dokpamine.domain.record.entity.*;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

class FeedApiTest extends ApiTestSupport {
    @Autowired ReviewLikeRepository likes;
    @Autowired BookReviewSearchRepository searchBooks;

    private JsonNode search(User me, String query) throws Exception {
        return request(get("/api/feed/books").param("query", query), me, null, 200);
    }

    @Test
    @DisplayName("FR-08 FR-09 NFR-09 공개 전환과 비공개 권한 및 좋아요 멱등을 유지한다")
    void privacyAndLikes() throws Exception {
        User a = user(), b = user();
        var book = book(a);
        var review = review(a, book, Visibility.PRIVATE, null);
        request(get("/api/feed/" + review.getId()), b, null, 404);
        request(get("/api/feed/" + review.getId()), a, null, 200);
        var dto =
                new com.dokpamine.domain.record.dto.ReviewDto(
                        review.getId(),
                        book.getId(),
                        "감상",
                        null,
                        Visibility.PUBLIC,
                        false,
                        MONDAY,
                        MONDAY,
                        review.getVersion());
        long version = request(put("/api/reviews/" + review.getId()), a, dto, 200)
                .path("version").asLong();
        for (int i = 0; i < 2; i++)
            assertEquals(
                    1,
                    request(post("/api/feed/" + review.getId() + "/like"), b, null, 200)
                            .path("likeCount")
                            .asInt());
        assertEquals(
                1,
                request(get("/api/feed/" + review.getId()), b, null, 200)
                        .path("likeCount")
                        .asInt());
        for (int i = 0; i < 2; i++)
            assertEquals(
                    0,
                    request(delete("/api/feed/" + review.getId() + "/like"), b, null, 200)
                            .path("likeCount")
                            .asInt());
        request(
                put("/api/reviews/" + review.getId()),
                a,
                new com.dokpamine.domain.record.dto.ReviewDto(
                        review.getId(),
                        book.getId(),
                        "감상",
                        null,
                        Visibility.PRIVATE,
                        false,
                        MONDAY,
                        MONDAY,
                        version),
                200);
        request(get("/api/feed/" + review.getId()), b, null, 404);
        request(post("/api/feed/" + review.getId() + "/like"), b, null, 404);
    }

    @Test
    @DisplayName("FR-16 ISBN10과 ISBN13 사본은 제목이 달라도 묶고 공개 응답과 좋아요를 보존한다")
    void isbnGroupsAndFields() throws Exception {
        User me = user(), other = user();
        var old = review(me, book(me, "데미안", "헤세", "8937460440"), Visibility.PUBLIC, MONDAY);
        var recent =
                review(
                        other,
                        book(other, "Demian", "Hesse", "9788937460449"),
                        Visibility.PUBLIC,
                        MONDAY.plusSeconds(10));
        review(me, book(me, "데미안", "헤세", "9788937460449"), Visibility.PRIVATE, null);
        likes.save(new ReviewLike(new ReviewLikeId(recent.getId(), me.getId())));
        JsonNode groups = search(me, "데미안");
        assertEquals(1, groups.size());
        assertEquals("isbn:9788937460449", groups.path(0).path("bookKey").asString());
        JsonNode items = groups.path(0).path("reviews");
        assertEquals(2, items.size());
        assertEquals(recent.getId().toString(), items.path(0).path("id").asString());
        assertEquals(old.getId().toString(), items.path(1).path("id").asString());
        assertTrue(items.path(0).path("likedByMe").asBoolean());
        assertEquals(1, items.path(0).path("likeCount").asInt());
        assertFalse(items.path(0).path("mine").asBoolean());
        assertTrue(items.path(1).path("mine").asBoolean());
        Set<String> fields = new HashSet<>();
        items.path(0).propertyNames().forEach(fields::add);
        assertEquals(
                Set.of(
                        "id",
                        "nickname",
                        "bookTitle",
                        "bookAuthor",
                        "genre",
                        "oneLine",
                        "body",
                        "publishedAt",
                        "likeCount",
                        "likedByMe",
                        "mine"),
                fields);
        assertEquals(items.path(0), request(get("/api/feed/" + recent.getId()), me, null, 200));
    }

    @Test
    @DisplayName("FR-16 ISBN 없는 책은 제목과 저자 모두 공백과 대소문자를 정규화하여 묶는다")
    void textGroups() throws Exception {
        User me = user();
        review(me, book(me, "  My\t Book  ", " Hermann   Hesse ", null), Visibility.PUBLIC, MONDAY);
        review(
                me,
                book(me, "my book", "hermann\u00a0hesse", "  "),
                Visibility.PUBLIC,
                MONDAY.plusSeconds(1));
        review(me, book(me, "MY BOOK", "다른 저자", null), Visibility.PUBLIC, MONDAY);
        JsonNode groups = search(me, " MY\t\u00a0BOOK ");
        assertEquals(2, groups.size());
        assertEquals(2, groups.path(0).path("reviews").size());
        assertTrue(groups.path(0).path("bookKey").asString().startsWith("text:"));
    }

    @Test
    @DisplayName("FR-16 다른 ISBN과 ISBN 없는 책은 별도 묶음이다")
    void distinctIsbns() throws Exception {
        User me = user();
        for (String isbn : Arrays.asList("9788937460449", "9788937460456", null))
            review(me, book(me, "데미안", "헤세", isbn), Visibility.PUBLIC, MONDAY);
        assertEquals(3, search(me, "데미안").size());
    }

    @Test
    @DisplayName("FR-16 NFR-09 비공개와 삭제 감상 및 삭제된 책은 검색에서 제외한다")
    void hiddenRecords() throws Exception {
        User me = user();
        review(me, book(me), Visibility.PRIVATE, null);
        Review deleted = review(me, book(me), Visibility.PUBLIC, MONDAY);
        deleted.setDeleted(true);
        UserBook book = book(me);
        book.setDeleted(true);
        review(me, book, Visibility.PUBLIC, MONDAY);
        assertEquals(0, search(me, "데미안").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    @DisplayName("FR-16 LIKE 특수문자는 일반 제목 문자로 검색한다")
    void literalLike(String value) throws Exception {
        User me = user();
        review(me, book(me, "제목" + value, "저자", null), Visibility.PUBLIC, MONDAY);
        review(me, book(me, "다른 제목", "저자", null), Visibility.PUBLIC, MONDAY);
        assertEquals(1, search(me, value).size());
    }

    @Test
    @DisplayName("FR-16 검색어 누락과 빈 값은 400이며 인증이 없으면 401이다")
    void invalidQuery() throws Exception {
        User me = user();
        for (String query : List.of("", " ", "\t\n"))
            assertEquals(
                    "BAD_REQUEST",
                    request(get("/api/feed/books").param("query", query), me, null, 400)
                            .path("code")
                            .asString());
        request(get("/api/feed/books"), me, null, 400);
        request(get("/api/feed/books?query=책"), null, null, 401);
        assertEquals(0, search(me, "없음").size());
    }

    @Test
    @DisplayName("FR-16 최대 20개 묶음과 묶음별 최신 감상 100개를 반환한다")
    void resultLimits() throws Exception {
        User me = user();
        for (int i = 0; i < 101; i++)
            review(me, book(me, "상한 검색", "인기", null), Visibility.PUBLIC, MONDAY.plusSeconds(i));
        for (int i = 0; i < 21; i++)
            review(
                    me,
                    book(me, "상한 검색", "저자" + i, null),
                    Visibility.PUBLIC,
                    MONDAY.minusSeconds(i + 1));
        JsonNode groups = search(me, "상한 검색");
        assertEquals(20, groups.size());
        assertEquals(100, groups.path(0).path("reviews").size());
        assertEquals(
                "2026-10-25T15:01:40Z",
                groups.path(0).path("reviews").path(0).path("publishedAt").asString());
    }

    @Test
    @DisplayName("FR-16 제목 후보는 최신 공개순 500개까지만 읽는다")
    void candidateLimit() throws Exception {
        User me = user();
        for (int i = 0; i < 501; i++)
            review(
                    me,
                    book(me, "후보 상한", "저자" + i, null),
                    Visibility.PUBLIC,
                    MONDAY.minusSeconds(i));
        em.flush();
        em.clear();
        var candidates =
                searchBooks.findTitleCandidates(
                        "%후보%상한%", Visibility.PUBLIC, PageRequest.of(0, 500));
        assertEquals(500, candidates.size());
        assertEquals("저자0", candidates.getFirst().getAuthor());
        assertEquals("저자499", candidates.getLast().getAuthor());
        assertEquals(20, search(me, "후보 상한").size());
    }

    @Test
    @DisplayName("FR-16 조회 결과 동점은 UUID 문자열 순이며 공개 시각 없음은 마지막이다")
    void ordering() throws Exception {
        User me = user();
        UUID first = UUID.fromString("00000000-0000-0000-8000-000000000000"),
                second = UUID.fromString("80000000-0000-0000-0000-000000000000");
        for (UUID id : List.of(second, first)) {
            var book = book(me, "동점", "저자", id.equals(first) ? "9788937460449" : "9788937460456");
            reviews.save(
                    new Review(
                            id,
                            me.getId(),
                            book.getId(),
                            "감상",
                            null,
                            Visibility.PUBLIC,
                            MONDAY,
                            false,
                            MONDAY,
                            MONDAY));
        }
        review(me, book(me, "동점", "시각 없음", null), Visibility.PUBLIC, null);
        JsonNode groups = search(me, "동점");
        assertEquals(
                first.toString(), groups.path(0).path("reviews").path(0).path("id").asString());
        assertTrue(groups.path(2).path("reviews").path(0).path("publishedAt").isNull());
    }

    @Test
    @DisplayName("FR-16 여러 묶음도 좋아요는 배치 조회하여 SQL 수가 일정하다")
    void batchQueries() throws Exception {
        User me = user(), other = user();
        for (int g = 0; g < 3; g++)
            for (int i = 0; i < 30; i++) {
                var review =
                        review(
                                other,
                                book(other, "배치" + g, "저자", null),
                                Visibility.PUBLIC,
                                MONDAY.plusSeconds(i));
                likes.save(new ReviewLike(new ReviewLikeId(review.getId(), me.getId())));
            }
        em.flush();
        em.clear();
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            JsonNode groups = search(me, "배치");
            assertEquals(3, groups.size());
            assertTrue(
                    stats.getPrepareStatementCount() <= 10,
                    "실제 SQL 수: " + stats.getPrepareStatementCount());
            assertTrue(groups.path(0).path("reviews").path(0).path("likedByMe").asBoolean());
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    @Test
    @DisplayName("FR-16 DB 시각 타입의 Instant 변환은 정밀도를 유지한다")
    void nativeDates() {
        Instant value = Instant.parse("2026-11-01T12:00:00.123456789Z");
        assertEquals(value, NativePublishedAt.toInstant(value));
        assertEquals(
                value,
                NativePublishedAt.toInstant(
                        OffsetDateTime.ofInstant(value, ZoneOffset.ofHours(9))));
        assertEquals(value, NativePublishedAt.toInstant(Timestamp.from(value)));
        assertNull(NativePublishedAt.toInstant(null));
    }
}
