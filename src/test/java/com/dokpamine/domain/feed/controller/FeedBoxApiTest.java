package com.dokpamine.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.domain.feed.entity.*;
import com.dokpamine.domain.feed.repository.ReviewLikeRepository;
import com.dokpamine.domain.record.dto.BookDto;
import com.dokpamine.domain.record.dto.ReviewDto;
import com.dokpamine.domain.record.entity.*;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.*;

class FeedBoxApiTest extends ApiTestSupport {
    @Autowired ReviewLikeRepository likes;
    private long clock;

    @Test
    @DisplayName("FR-08 같은 책의 공개 독후감은 박스 하나로 묶고 나중에 새로 쓴 독후감을 보여 준다")
    void groupsByUserBook() throws Exception {
        User owner = user(), reader = user();
        UserBook book = book(owner);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        write(owner, first, book, "첫 한줄평", "첫 독후감", Visibility.PUBLIC, null);
        write(owner, second, book, null, "두 번째 독후감", Visibility.PUBLIC, null);

        List<JsonNode> feed = feed(reader);
        assertEquals(1, feed.size());
        assertEquals(second.toString(), feed.getFirst().path("id").asString());
        assertEquals(book.getId().toString(), feed.getFirst().path("userBookId").asString());
        assertEquals("두 번째 독후감", feed.getFirst().path("body").asString());
        assertFalse(feed.getFirst().path("bodyWrittenAt").isNull());
    }

    @Test
    @DisplayName("FR-08 FR-16 한줄평만 공개된 책은 라운지 박스로 뜨지 않고 책별 감상 검색에는 나온다")
    void oneLineOnlyBookIsSearchableButNotBoxed() throws Exception {
        User owner = user(), reader = user();
        UserBook book = book(owner, "한줄평만 쓴 책", "저자", null);
        write(owner, UUID.randomUUID(), book, "한줄평만", null, Visibility.PUBLIC, null);

        assertEquals(List.of(), feed(reader));
        JsonNode groups =
                request(get("/api/feed/books").param("query", "한줄평만"), reader, null, 200);
        assertEquals(1, groups.size());
        assertEquals(1, groups.path(0).path("reviews").size());
    }

    @Test
    @DisplayName("FR-08 한줄평 쓰기·수정, 독후감 수정, 공개 전환으로는 박스가 위로 올라오지 않는다")
    void editsAndPublishingDoNotBump() throws Exception {
        User owner = user(), other = user(), reader = user();
        UserBook a = book(owner), b = book(other);
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID(), a3 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        JsonNode first = write(owner, a1, a, "한줄평", "독후감", Visibility.PUBLIC, null);
        JsonNode hidden = write(owner, a3, a, null, "비공개 독후감", Visibility.PRIVATE, null);
        write(other, b1, b, null, "다른 책 독후감", Visibility.PUBLIC, null);
        assertEquals(List.of(b1.toString(), a1.toString()), ids(feed(reader)));

        JsonNode edited = write(owner, a1, a, "한줄평", "고친 독후감", Visibility.PUBLIC, first);
        write(owner, a1, a, "고친 한줄평", "고친 독후감", Visibility.PUBLIC, edited);
        write(owner, a2, a, "새 한줄평", null, Visibility.PUBLIC, null);
        write(owner, a3, a, null, "비공개 독후감", Visibility.PUBLIC, hidden);

        // 공개로 바꾼 독후감은 원래 쓴 시각 자리에 들어가 대표가 되지만 B보다 위로 가지 않는다.
        assertEquals(List.of(b1.toString(), a3.toString()), ids(feed(reader)));
    }

    @Test
    @DisplayName("FR-08 한줄평만 있던 감상에 독후감을 더하면 새로 쓴 것으로 보고 박스가 맨 위로 올라온다")
    void addingBodyToOneLineBumps() throws Exception {
        User owner = user(), other = user(), reader = user();
        UserBook a = book(owner), b = book(other);
        UUID a1 = UUID.randomUUID(), b1 = UUID.randomUUID();
        JsonNode oneLine = write(owner, a1, a, "한줄평", null, Visibility.PUBLIC, null);
        write(other, b1, b, null, "독후감", Visibility.PUBLIC, null);
        assertEquals(List.of(b1.toString()), ids(feed(reader)));

        write(owner, a1, a, "한줄평", "나중에 쓴 독후감", Visibility.PUBLIC, oneLine);
        assertEquals(List.of(a1.toString(), b1.toString()), ids(feed(reader)));
    }

    @Test
    @DisplayName("FR-08 독후감을 지웠다가 다시 써도 처음 쓴 시각을 유지한다")
    void rewritingBodyKeepsFirstWrittenAt() throws Exception {
        User owner = user(), other = user(), reader = user();
        UserBook a = book(owner), b = book(other);
        UUID a1 = UUID.randomUUID(), b1 = UUID.randomUUID();
        JsonNode created = write(owner, a1, a, "한줄평", "독후감", Visibility.PUBLIC, null);
        String writtenAt = feed(reader).getFirst().path("bodyWrittenAt").asString();
        write(other, b1, b, null, "독후감", Visibility.PUBLIC, null);

        JsonNode cleared = write(owner, a1, a, "한줄평", null, Visibility.PUBLIC, created);
        assertEquals(List.of(b1.toString()), ids(feed(reader)));
        write(owner, a1, a, "한줄평", "다시 쓴 독후감", Visibility.PUBLIC, cleared);

        List<JsonNode> feed = feed(reader);
        assertEquals(List.of(b1.toString(), a1.toString()), ids(feed));
        assertEquals(writtenAt, feed.get(1).path("bodyWrittenAt").asString());
    }

    @Test
    @DisplayName("FR-08 NFR-09 책 박스는 공개 한줄평과 독후감만 모두 반환하고 없으면 404다")
    void bookBoxReturnsPublicReviewsOnly() throws Exception {
        User owner = user(), reader = user();
        UserBook book = book(owner);
        Review oneLine = review(owner, book, Visibility.PUBLIC, MONDAY);
        oneLine.setBody(null);
        oneLine.setBodyWrittenAt(null);
        Review essay = review(owner, book, Visibility.PUBLIC, MONDAY.plusSeconds(10));
        review(owner, book, Visibility.PRIVATE, null);
        review(owner, book, Visibility.PUBLIC, MONDAY.plusSeconds(20)).setDeleted(true);
        likes.save(new ReviewLike(new ReviewLikeId(essay.getId(), reader.getId())));
        UserBook privateOnly = book(owner);
        review(owner, privateOnly, Visibility.PRIVATE, null);
        em.flush();
        em.clear();

        String path = "/api/feed/user-books/" + book.getId();
        List<JsonNode> items = list(request(get(path), reader, null, 200));
        assertEquals(List.of(essay.getId().toString(), oneLine.getId().toString()), ids(items));
        assertTrue(items.getFirst().path("likedByMe").asBoolean());
        assertFalse(items.getFirst().path("mine").asBoolean());
        assertTrue(items.get(1).path("body").isNull());
        assertTrue(list(request(get(path), owner, null, 200)).getFirst().path("mine").asBoolean());

        request(get("/api/feed/user-books/" + privateOnly.getId()), owner, null, 404);
        request(get("/api/feed/user-books/" + UUID.randomUUID()), reader, null, 404);
        request(get("/api/feed/user-books/not-a-uuid"), reader, null, 400);
        request(get(path), null, null, 401);

        var deletedBook = json.valueToTree(new BookDto(books.findById(book.getId()).orElseThrow()))
                .asObject()
                .put("deleted", true);
        request(put("/api/books/" + book.getId()), owner, deletedBook, 200);
        request(get(path), reader, null, 404);
        assertEquals(List.of(), feed(reader));
    }

    private JsonNode write(
            User owner,
            UUID id,
            UserBook book,
            String oneLine,
            String body,
            Visibility visibility,
            JsonNode previous)
            throws Exception {
        Long version = previous == null ? null : previous.required("version").asLong();
        Instant updatedAt = MONDAY.plusSeconds(++clock);
        var dto =
                new ReviewDto(
                        id, book.getId(), oneLine, body, visibility, false, MONDAY, updatedAt,
                        version);
        return request(put("/api/reviews/" + id), owner, dto, 200);
    }

    private List<JsonNode> feed(User viewer) throws Exception {
        return list(request(get("/api/feed"), viewer, null, 200));
    }

    private static List<String> ids(List<JsonNode> items) {
        return items.stream().map(item -> item.path("id").asString()).toList();
    }
}
