package com.dokpamine.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.domain.record.dto.*;
import com.dokpamine.domain.record.entity.*;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;

import java.time.*;
import java.util.*;

class RecordApiTest extends ApiTestSupport {
    private SessionDto dto(UUID bookId, Instant start) {
        return new SessionDto(
                UUID.randomUUID(),
                bookId,
                start,
                start.plusSeconds(600),
                600,
                30,
                50,
                SessionSource.TIMER,
                false,
                start);
    }

    private Map<String, Object> changed(SessionDto dto, String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dto.id());
        map.put("bookId", dto.bookId());
        map.put("startedAt", dto.startedAt());
        map.put("endedAt", dto.endedAt());
        map.put("activeSeconds", dto.activeSeconds());
        map.put("startPage", dto.startPage());
        map.put("endPage", dto.endPage());
        map.put("source", dto.source());
        map.put("deleted", dto.deleted());
        map.put("createdAt", dto.createdAt());
        map.put(key, value);
        return map;
    }

    private JsonNode save(User user, SessionDto dto) throws Exception {
        return request(put("/api/sessions/" + dto.id()), user, dto, 200);
    }

    private JsonNode snapshot(User user) throws Exception {
        return request(get("/api/sync/snapshot"), user, null, 200);
    }

    @Test
    @DisplayName("NFR-04 NFR-05 FR-15 책만 먼저 삭제해도 하위 기록은 스냅샷과 랭킹에서 빠진다")
    void bookDeletionHidesChildRecords() throws Exception {
        User owner = user("참여", true);
        UserBook removed = book(owner);
        UserBook kept = book(owner);
        var removedSession = dto(removed.getId(), MONDAY);
        var keptSession = dto(kept.getId(), MONDAY.plusSeconds(600));
        save(owner, removedSession);
        save(owner, keptSession);
        var removedReview = review(owner, removed, Visibility.PUBLIC, MONDAY);
        var keptReview = review(owner, kept, Visibility.PRIVATE, null);
        var removedQuote = new QuoteDto(UUID.randomUUID(), removed.getId(), "문장", 12, null, false, MONDAY, MONDAY);
        var keptQuote = new QuoteDto(UUID.randomUUID(), kept.getId(), "문장", 12, null, false, MONDAY, MONDAY);
        request(put("/api/quotes/" + removedQuote.id()), owner, removedQuote, 200);
        request(put("/api/quotes/" + keptQuote.id()), owner, keptQuote, 200);

        String ranking = "/api/ranking/weekly?weekStart=2026-10-26";
        assertEquals(1200, request(get(ranking), owner, null, 200).path("top").path(0).path("seconds").asInt());
        var deletedBook = json.valueToTree(new BookDto(removed)).asObject().put("deleted", true);
        request(put("/api/books/" + removed.getId()), owner, deletedBook, 200);

        JsonNode snapshot = snapshot(owner);
        Map<String, UUID> keptIds = Map.of(
                "books", kept.getId(),
                "sessions", keptSession.id(),
                "reviews", keptReview.getId(),
                "quotes", keptQuote.id());
        keptIds.forEach((key, id) -> assertEquals(
                List.of(id.toString()),
                list(snapshot.path(key)).stream().map(node -> node.path("id").asString()).toList(),
                key));
        assertEquals(600, request(get(ranking), owner, null, 200).path("top").path(0).path("seconds").asInt());

        var sessionDeletion = changed(removedSession, "deleted", true);
        var reviewDeletion = json.valueToTree(new ReviewDto(removedReview)).asObject().put("deleted", true);
        var quoteDeletion = json.valueToTree(removedQuote).asObject().put("deleted", true);
        for (int i = 0; i < 2; i++) {
            request(put("/api/sessions/" + removedSession.id()), owner, sessionDeletion, 200);
            request(put("/api/reviews/" + removedReview.getId()), owner, reviewDeletion, 200);
            request(put("/api/quotes/" + removedQuote.id()), owner, quoteDeletion, 200);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-60, 0, 60})
    @DisplayName("NFR-05 NFR-09 삭제된 책은 요청 시각과 무관하게 복원되지 않고 공개 감상도 숨긴다")
    void staleBookCannotUndoDeletion(long offsetSeconds) throws Exception {
        User owner = user(), reader = user();
        UUID id = UUID.randomUUID();
        String path = "/api/books/" + id;
        var original = new BookDto(id, null, "책", "저자", null, 100, "소설", null,
                BookStatus.READING, 10, 0, null, false, MONDAY, MONDAY);
        request(put(path), owner, original, 200);
        var review = review(owner, books.findById(id).orElseThrow(), Visibility.PUBLIC, MONDAY);
        request(get("/api/feed/" + review.getId()), reader, null, 200);

        Instant deletedAt = MONDAY.plusSeconds(120);
        var deletion = json.valueToTree(original).asObject()
                .put("deleted", true).put("updatedAt", deletedAt.toString());
        JsonNode deleted = request(put(path), owner, deletion, 200);
        request(get("/api/feed/" + review.getId()), reader, null, 404);
        var stale = json.valueToTree(original).asObject()
                .put("title", "오래된 기기의 수정").put("updatedAt", deletedAt.plusSeconds(offsetSeconds).toString());
        request(put(path), reader, stale, 403);
        for (int i = 0; i < 2; i++) {
            assertEquals(deleted, request(put(path), owner, stale, 200));
            assertEquals(deleted, request(put(path), owner, deletion, 200));
            JsonNode snapshot = snapshot(owner);
            assertEquals(0, snapshot.path("books").size());
            assertEquals(0, snapshot.path("reviews").size());
            assertEquals(0, request(get("/api/feed"), reader, null, 200).size());
            request(get("/api/feed/" + review.getId()), reader, null, 404);
        }
        assertEquals(1, books.count());
        UserBook saved = books.findById(id).orElseThrow();
        assertTrue(saved.getDeleted());
        assertEquals(deletedAt, saved.getUpdatedAt());
        assertEquals("책", saved.getTitle());
        assertFalse(reviews.findById(review.getId()).orElseThrow().getDeleted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456123", "123456789", "999999999"})
    @DisplayName("NFR-05 같은 세션 재전송은 DB 마이크로초 반올림 이후에도 TIMER 한 건이다")
    void idempotent(String nanos) throws Exception {
        User user = user();
        var dto = dto(book(user).getId(), Instant.parse("2026-10-26T03:00:00." + nanos + "Z"));
        for (int i = 0; i < 3; i++)
            assertEquals("TIMER", save(user, dto).path("source").asString());
        JsonNode saved = snapshot(user).path("sessions");
        assertEquals(1, saved.size());
        assertEquals(
                "TIMER",
                request(put("/api/sessions/" + dto.id()), user, saved.path(0), 200)
                        .path("source")
                        .asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"startedAt", "endedAt", "activeSeconds"})
    @DisplayName("T09 시간 수정은 MANUAL로 전환되어 랭킹에서 제외된다")
    void changedTime(String field) throws Exception {
        User user = user("참여", true);
        var dto = dto(book(user).getId(), MONDAY);
        save(user, dto);
        assertEquals(
                1,
                request(get("/api/ranking/weekly?weekStart=2026-10-26"), user, null, 200)
                        .path("top")
                        .size());
        Object value =
                switch (field) {
                    case "startedAt" -> dto.startedAt().minusSeconds(1);
                    case "endedAt" -> dto.endedAt().plusNanos(1000);
                    default -> 660;
                };
        assertEquals(
                "MANUAL",
                request(put("/api/sessions/" + dto.id()), user, changed(dto, field, value), 200)
                        .path("source")
                        .asString());
        assertEquals(
                0,
                request(get("/api/ranking/weekly?weekStart=2026-10-26"), user, null, 200)
                        .path("top")
                        .size());
    }

    @Test
    @DisplayName("T09 MANUAL은 TIMER로 복귀하지 않으며 페이지와 삭제 변경은 시간을 바꾸지 않는다")
    void sourceAndNonTimeEdits() throws Exception {
        User user = user();
        var dto = dto(book(user).getId(), MONDAY);
        save(user, dto);
        assertEquals(
                "TIMER",
                request(put("/api/sessions/" + dto.id()), user, changed(dto, "startPage", 40), 200)
                        .path("source")
                        .asString());
        assertEquals(
                "TIMER",
                request(put("/api/sessions/" + dto.id()), user, changed(dto, "deleted", true), 200)
                        .path("source")
                        .asString());
        assertEquals(0, snapshot(user).path("sessions").size());
        save(user, dto);
        assertEquals(
                "MANUAL",
                request(
                                put("/api/sessions/" + dto.id()),
                                user,
                                changed(dto, "source", "MANUAL"),
                                200)
                        .path("source")
                        .asString());
        assertEquals("MANUAL", save(user, dto).path("source").asString());
    }

    @Test
    @DisplayName("NFR-05 전체 쪽수를 줄인 뒤에도 소유자가 기존 세션을 삭제하면 랭킹에서 제외된다")
    void deleteSessionAfterReducingTotalPages() throws Exception {
        User owner = user("참여", true);
        UserBook book = book(owner);
        var session = dto(book.getId(), MONDAY);
        save(owner, session);

        var updatedBook = new BookDto(
                book.getId(), null, "책", "저자", null, 40, "소설", null,
                BookStatus.READING, 0, 0, null, false, MONDAY, MONDAY);
        request(put("/api/books/" + book.getId()), owner, updatedBook, 200);

        String sessionPath = "/api/sessions/" + session.id();
        String rankingPath = "/api/ranking/weekly?weekStart=2026-10-26";
        var deletion = changed(session, "deleted", true);
        request(put(sessionPath), owner, session, 400);
        var newSessionDeletion = changed(dto(book.getId(), MONDAY), "deleted", true);
        request(put("/api/sessions/" + newSessionDeletion.get("id")), owner, newSessionDeletion, 400);
        request(put(sessionPath), user(), deletion, 403);
        assertEquals(1, request(get(rankingPath), owner, null, 200).path("top").size());

        for (int i = 0; i < 2; i++) {
            assertTrue(request(put(sessionPath), owner, deletion, 200).path("deleted").asBoolean());
        }
        assertEquals(0, snapshot(owner).path("sessions").size());
        assertEquals(0, request(get(rankingPath), owner, null, 200).path("top").size());
    }

    @Test
    @DisplayName("NFR-05 검증 실패와 타인 덮어쓰기는 원래 세션을 보존한다")
    void rejectInvalidUpdates() throws Exception {
        User owner = user(), other = user();
        var dto = dto(book(owner).getId(), MONDAY);
        save(owner, dto);
        assertEquals(
                "BOOK_NOT_SYNCED",
                request(
                                put("/api/sessions/" + dto.id()),
                                owner,
                                changed(dto, "bookId", UUID.randomUUID()),
                                409)
                        .path("code")
                        .asString());
        request(
                put("/api/sessions/" + dto.id()),
                owner,
                changed(dto, "endedAt", MONDAY.minusSeconds(1)),
                400);
        request(put("/api/sessions/" + dto.id()), owner, changed(dto, "endPage", 300), 400);
        request(
                put("/api/sessions/" + dto.id()),
                other,
                changed(dto, "bookId", book(other).getId()),
                403);
        request(put("/api/sessions/" + UUID.randomUUID()), owner, dto, 400);
        assertEquals("TIMER", snapshot(owner).path("sessions").path(0).path("source").asString());
    }

    @Test
    @DisplayName("NFR-05 책과 인용문 PUT은 멱등이며 삭제한 기록은 snapshot에서 제외된다")
    void booksAndQuotes() throws Exception {
        User owner = user(), other = user();
        UUID id = UUID.randomUUID();
        var dto =
                new BookDto(
                        id,
                        null,
                        " 책 ",
                        " 저자 ",
                        null,
                        100,
                        "소설",
                        null,
                        BookStatus.READING,
                        10,
                        0,
                        null,
                        false,
                        MONDAY,
                        MONDAY);
        for (int i = 0; i < 2; i++)
            assertEquals(
                    "책",
                    request(put("/api/books/" + id), owner, dto, 200).path("title").asString());
        request(put("/api/books/" + id), other, dto, 403);
        var quote = new QuoteDto(UUID.randomUUID(), id, " 문장 ", 12, "메모", false, MONDAY, MONDAY);
        for (int i = 0; i < 2; i++) request(put("/api/quotes/" + quote.id()), owner, quote, 200);
        assertEquals(1, snapshot(owner).path("quotes").size());
        request(put("/api/quotes/" + quote.id()), other, quote, 403);
        var deleted = new QuoteDto(quote.id(), id, "문장", 12, null, true, MONDAY, MONDAY);
        request(put("/api/quotes/" + quote.id()), owner, deleted, 200);
        assertEquals(0, snapshot(owner).path("quotes").size());
        request(
                put("/api/books/" + id),
                owner,
                new BookDto(
                        id,
                        null,
                        "책",
                        "저자",
                        null,
                        100,
                        "소설",
                        null,
                        BookStatus.READING,
                        101,
                        0,
                        null,
                        false,
                        MONDAY,
                        MONDAY),
                400);
    }

    @Test
    @DisplayName("NFR-09 감상 visibility 생략은 PRIVATE이며 빈 감상은 거절한다")
    void reviewDefaults() throws Exception {
        User owner = user();
        var book = book(owner);
        UUID id = UUID.randomUUID();
        Map<String, Object> body =
                new LinkedHashMap<>(
                        Map.of(
                                "id",
                                id,
                                "bookId",
                                book.getId(),
                                "oneLine",
                                " 한 줄 ",
                                "createdAt",
                                MONDAY,
                                "updatedAt",
                                MONDAY));
        assertEquals(
                "PRIVATE",
                request(put("/api/reviews/" + id), owner, body, 200).path("visibility").asString());
        body.put("oneLine", "");
        request(put("/api/reviews/" + id), owner, body, 400);
    }

    @Test
    @DisplayName("NFR-09 지연된 공개 수정과 버전 없는 수정은 최신 비공개 감상을 바꾸지 않는다")
    void staleReviewCannotBecomePublicAgain() throws Exception {
        User owner = user(), reader = user();
        UUID id = UUID.randomUUID(), bookId = book(owner).getId();
        String path = "/api/reviews/" + id;
        var create = new ReviewDto(
                id, bookId, "공개 감상", null, Visibility.PUBLIC, false, MONDAY, MONDAY, null);
        var created = request(put(path), owner, create, 200);
        assertEquals(0, created.required("version").asLong());
        assertEquals(created, request(put(path), owner, create, 200));
        request(get("/api/feed/" + id), reader, null, 200);

        long base = created.required("version").asLong();
        var hide = new ReviewDto(
                id, bookId, "공개 감상", null, Visibility.PRIVATE, false, MONDAY, MONDAY, base);
        var hidden = request(put(path), owner, hide, 200);
        assertEquals(base + 1, hidden.required("version").asLong());
        assertEquals(hidden, request(put(path), owner, hide, 200));

        var stale = new ReviewDto(
                id, bookId, "오프라인 수정", null, Visibility.PUBLIC, false,
                MONDAY, MONDAY.plusSeconds(60), base);
        assertEquals("REVIEW_VERSION_CONFLICT",
                request(put(path), owner, stale, 409).path("code").asString());
        assertEquals("REVIEW_VERSION_CONFLICT",
                request(put(path), owner, create, 409).path("code").asString());
        request(put(path), reader, stale, 403);
        assertEquals(1, snapshot(owner).path("reviews").size());
        assertEquals(hidden, snapshot(owner).path("reviews").path(0));
        assertEquals(0, request(get("/api/feed"), reader, null, 200).size());
        request(get("/api/feed/" + id), reader, null, 404);
        assertNull(reviews.findById(id).orElseThrow().getPublishedAt());
    }

    @Test
    @DisplayName("NFR-05 오래된 감상은 삭제를 되돌리지 못하고 같은 삭제 요청은 재전송할 수 있다")
    void staleReviewCannotUndoDeletion() throws Exception {
        User owner = user();
        UUID id = UUID.randomUUID(), bookId = book(owner).getId();
        String path = "/api/reviews/" + id;
        var create = new ReviewDto(
                id, bookId, "감상", null, Visibility.PRIVATE, false, MONDAY, MONDAY, null);
        long base = request(put(path), owner, create, 200).required("version").asLong();
        var delete = new ReviewDto(
                id, bookId, null, null, Visibility.PRIVATE, true, MONDAY, MONDAY, base);
        var deleted = request(put(path), owner, delete, 200);
        assertEquals(base + 1, deleted.required("version").asLong());
        assertEquals(deleted, request(put(path), owner, delete, 200));
        assertEquals("REVIEW_VERSION_CONFLICT",
                request(put(path), owner, create, 409).path("code").asString());
        assertEquals(0, snapshot(owner).path("reviews").size());
    }

    @Test
    @DisplayName("NFR-05 모든 기록에서 deleted 생략은 false이며 필수 숫자는 생략할 수 없다")
    void omittedFields() throws Exception {
        User owner = user();
        var book = book(owner);
        var session = dto(book.getId(), MONDAY);
        var review =
                new ReviewDto(
                        UUID.randomUUID(),
                        book.getId(),
                        "감상",
                        null,
                        Visibility.PRIVATE,
                        false,
                        MONDAY,
                        MONDAY,
                        null);
        var quote =
                new QuoteDto(
                        UUID.randomUUID(), book.getId(), "문장", null, null, false, MONDAY, MONDAY);
        Map<String, Object> records =
                Map.of(
                        "/api/books/" + book.getId(), new BookDto(book),
                        "/api/sessions/" + session.id(), session,
                        "/api/reviews/" + review.id(), review,
                        "/api/quotes/" + quote.id(), quote);
        for (var record : records.entrySet()) {
            var body = (tools.jackson.databind.node.ObjectNode) json.valueToTree(record.getValue());
            body.remove("deleted");
            assertFalse(
                    request(put(record.getKey()), owner, body, 200).path("deleted").asBoolean());
        }
        var body = (tools.jackson.databind.node.ObjectNode) json.valueToTree(session);
        body.remove("activeSeconds");
        request(put("/api/sessions/" + session.id()), owner, body, 400);
    }

    @Test
    @DisplayName("NFR-05 명시적인 deleted null은 삭제 기록을 복원하지 않고 400으로 거절한다")
    void explicitNullDoesNotRestoreDeletedRecords() throws Exception {
        User owner = user();
        var book = book(owner);
        var originalBook = new BookDto(book);
        var deletedBook =
                new BookDto(
                        originalBook.id(),
                        originalBook.isbn(),
                        originalBook.title(),
                        originalBook.author(),
                        originalBook.publisher(),
                        originalBook.totalPages(),
                        originalBook.genre(),
                        originalBook.coverUrl(),
                        originalBook.status(),
                        originalBook.currentPage(),
                        originalBook.completedCount(),
                        originalBook.completedAt(),
                        true,
                        originalBook.createdAt(),
                        originalBook.updatedAt());
        var session = dto(book.getId(), MONDAY);
        var deletedSession =
                new SessionDto(
                        session.id(),
                        session.bookId(),
                        session.startedAt(),
                        session.endedAt(),
                        session.activeSeconds(),
                        session.startPage(),
                        session.endPage(),
                        session.source(),
                        true,
                        session.createdAt());
        var review =
                new ReviewDto(
                        UUID.randomUUID(),
                        book.getId(),
                        "감상",
                        null,
                        Visibility.PRIVATE,
                        true,
                        MONDAY,
                        MONDAY,
                        null);
        var quote =
                new QuoteDto(
                        UUID.randomUUID(), book.getId(), "문장", null, null, true, MONDAY, MONDAY);
        Map<String, Object> records =
                Map.of(
                        "/api/books/" + book.getId(), deletedBook,
                        "/api/sessions/" + session.id(), deletedSession,
                        "/api/reviews/" + review.id(), review,
                        "/api/quotes/" + quote.id(), quote);
        for (var record : records.entrySet()) {
            request(put(record.getKey()), owner, record.getValue(), 200);
            var body = (tools.jackson.databind.node.ObjectNode) json.valueToTree(record.getValue());
            body.putNull("deleted");
            request(put(record.getKey()), owner, body, 400);
        }
        JsonNode snapshot = snapshot(owner);
        for (String resource : List.of("books", "sessions", "reviews", "quotes")) {
            assertEquals(0, snapshot.path(resource).size());
        }
    }

    @Test
    @DisplayName("NFR-09 명시적인 visibility null은 공개 감상을 변경하지 않고 400으로 거절한다")
    void explicitNullDoesNotHidePublicReview() throws Exception {
        User owner = user();
        var book = book(owner);
        var review =
                new ReviewDto(
                        UUID.randomUUID(),
                        book.getId(),
                        "공개 감상",
                        null,
                        Visibility.PUBLIC,
                        false,
                        MONDAY,
                        MONDAY,
                        null);
        String path = "/api/reviews/" + review.id();
        request(put(path), owner, review, 200);
        var before = reviews.findById(review.id()).orElseThrow();
        Instant publishedAt = before.getPublishedAt();
        var body = (tools.jackson.databind.node.ObjectNode) json.valueToTree(review);
        body.putNull("visibility");
        request(put(path), owner, body, 400);
        var saved = reviews.findById(review.id()).orElseThrow();
        assertEquals(Visibility.PUBLIC, saved.getVisibility());
        assertEquals(publishedAt, saved.getPublishedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u00a0", "\u2007", "\u202f", "\u3000"})
    @DisplayName("FR-08 Unicode 공백뿐인 감상은 거절하고 본문 양끝 공백은 제거한다")
    void unicodeReviewWhitespace(String whitespace) throws Exception {
        User owner = user();
        var book = book(owner);
        UUID id = UUID.randomUUID();
        String path = "/api/reviews/" + id;
        request(
                put(path),
                owner,
                new ReviewDto(
                        id,
                        book.getId(),
                        whitespace,
                        null,
                        Visibility.PRIVATE,
                        false,
                        MONDAY,
                        MONDAY,
                        null),
                400);
        request(
                put(path),
                owner,
                new ReviewDto(
                        id,
                        book.getId(),
                        null,
                        whitespace,
                        Visibility.PRIVATE,
                        false,
                        MONDAY,
                        MONDAY,
                        null),
                400);
        JsonNode saved =
                request(
                        put(path),
                        owner,
                        new ReviewDto(
                                id,
                                book.getId(),
                                whitespace + "요약" + whitespace,
                                whitespace + "본문" + whitespace,
                                Visibility.PRIVATE,
                                false,
                                MONDAY,
                                MONDAY,
                                null),
                        200);
        assertEquals("요약", saved.path("oneLine").asString());
        assertEquals("본문", saved.path("body").asString());
    }

    @Test
    @DisplayName("NFR-05 책 제목과 저자 및 인용문의 Unicode 공백 제거를 유지한다")
    void unicodeBookAndQuoteTrimming() throws Exception {
        User owner = user();
        UUID bookId = UUID.randomUUID();
        var book =
                new BookDto(
                        bookId,
                        null,
                        "\u00a0책\u3000",
                        "\u2007저자\u202f",
                        null,
                        100,
                        "소설",
                        null,
                        BookStatus.READING,
                        0,
                        0,
                        null,
                        false,
                        MONDAY,
                        MONDAY);
        JsonNode savedBook = request(put("/api/books/" + bookId), owner, book, 200);
        assertEquals("책", savedBook.path("title").asString());
        assertEquals("저자", savedBook.path("author").asString());
        var quote =
                new QuoteDto(
                        UUID.randomUUID(),
                        bookId,
                        "\u00a0문장\u3000",
                        null,
                        null,
                        false,
                        MONDAY,
                        MONDAY);
        assertEquals(
                "문장",
                request(put("/api/quotes/" + quote.id()), owner, quote, 200)
                        .path("text")
                        .asString());
    }
}
