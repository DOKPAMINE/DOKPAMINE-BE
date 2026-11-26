package com.dokpamine.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.dokpamine.domain.feed.entity.ReviewLike;
import com.dokpamine.domain.feed.entity.ReviewLikeId;
import com.dokpamine.domain.feed.repository.ReviewLikeRepository;
import com.dokpamine.domain.record.entity.*;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

import java.util.*;

class LikedBookApiTest extends ApiTestSupport {
    @Autowired ReviewLikeRepository likes;

    @Test
    @DisplayName("FR-09 좋아요한 공개 감상을 같은 책끼리 묶고 작성자의 책마다 좋아요 수를 준다")
    void groupsLikedReviewsByBookAndPerson() throws Exception {
        User me = user("나", false), sky = user("하늘", false), star = user("별", false);
        UserBook skyBook = book(sky, "어린 왕자", "생텍쥐페리", "9788932917245");
        // ISBN-10 표기로 등록해도 같은 책으로 묶는다.
        UserBook starBook = book(star, "어린왕자", "생텍쥐페리", "89-329-1724-8");
        starBook.setCoverUrl("https://example.com/prince.jpg");
        UserBook other = book(star, "데미안", "헤르만 헤세", null);
        Review sky1 = review(sky, skyBook, Visibility.PUBLIC, MONDAY);
        Review sky2 = review(sky, skyBook, Visibility.PUBLIC, MONDAY.plusSeconds(1));
        Review star1 = review(star, starBook, Visibility.PUBLIC, MONDAY);
        Review demian = review(star, other, Visibility.PUBLIC, MONDAY);
        like(me, star1, 10);
        like(me, sky1, 20);
        like(me, sky2, 30);
        like(me, demian, 5);
        like(sky, demian, 100);

        JsonNode page = liked(me, "size=20");
        List<JsonNode> items = list(page.path("items"));
        assertEquals(2, items.size());
        assertTrue(page.path("nextCursor").isNull());

        JsonNode prince = items.getFirst();
        assertEquals("isbn:9788932917245", prince.path("bookKey").asString());
        assertEquals("9788932917245", prince.path("isbn").asString());
        assertEquals("어린 왕자", prince.path("bookTitle").asString());
        assertEquals("https://example.com/prince.jpg", prince.path("coverUrl").asString());
        assertEquals(MONDAY.plusSeconds(30).toString(), prince.path("lastLikedAt").asString());
        List<JsonNode> people = list(prince.path("people"));
        assertEquals(2, people.size());
        assertEquals(sky.getId().toString(), people.get(0).path("authorId").asString());
        assertEquals("하늘", people.get(0).path("nickname").asString());
        assertEquals(skyBook.getId().toString(), people.get(0).path("userBookId").asString());
        assertEquals(2, people.get(0).path("likedCount").asInt());
        assertFalse(people.get(0).path("mine").asBoolean());
        assertEquals("별", people.get(1).path("nickname").asString());
        assertEquals(1, people.get(1).path("likedCount").asInt());

        JsonNode demianGroup = items.get(1);
        assertTrue(demianGroup.path("bookKey").asString().startsWith("text:"));
        assertTrue(demianGroup.path("isbn").isNull());
        assertTrue(demianGroup.path("coverUrl").isNull());
    }

    @Test
    @DisplayName("FR-09 NFR-09 삭제되거나 비공개로 바뀐 감상과 삭제된 책은 빼고 다시 공개되면 다시 보여 준다")
    void excludesHiddenReviews() throws Exception {
        User me = user(), owner = user();
        UserBook shown = book(owner, "보이는 책", "저자", null);
        UserBook deletedBook = book(owner, "삭제된 책", "저자", null);
        Review visible = review(owner, shown, Visibility.PUBLIC, MONDAY);
        Review hidden = review(owner, shown, Visibility.PUBLIC, MONDAY);
        Review removed = review(owner, shown, Visibility.PUBLIC, MONDAY);
        Review inDeletedBook = review(owner, deletedBook, Visibility.PUBLIC, MONDAY);
        like(me, visible, 1);
        like(me, hidden, 2);
        like(me, removed, 3);
        like(me, inDeletedBook, 4);
        hidden.setVisibility(Visibility.PRIVATE);
        removed.setDeleted(true);
        deletedBook.setDeleted(true);

        List<JsonNode> items = list(liked(me, "").path("items"));
        assertEquals(1, items.size());
        assertEquals(1, items.getFirst().path("people").path(0).path("likedCount").asInt());

        reviews.findById(hidden.getId()).orElseThrow().setVisibility(Visibility.PUBLIC);
        items = list(liked(me, "").path("items"));
        assertEquals(2, items.getFirst().path("people").path(0).path("likedCount").asInt());
    }

    @Test
    @DisplayName("FR-09 size만큼 책 묶음을 주고 nextCursor로 남은 묶음을 중복 없이 이어 준다")
    void pagesByBookGroup() throws Exception {
        User me = user(), owner = user();
        List<String> titles = List.of("첫째 책", "둘째 책", "셋째 책");
        for (int i = 0; i < titles.size(); i++) {
            UserBook book = book(owner, titles.get(i), "저자", null);
            // 한 책에 좋아요가 여러 개여도 페이지 경계에서 나뉘지 않아야 한다.
            like(me, review(owner, book, Visibility.PUBLIC, MONDAY), 100 - i * 10);
            like(me, review(owner, book, Visibility.PUBLIC, MONDAY), 100 - i * 10 - 1);
        }

        JsonNode first = liked(me, "size=2");
        assertEquals(List.of("첫째 책", "둘째 책"), titles(first));
        String cursor = first.path("nextCursor").asString();
        assertFalse(cursor.isBlank());

        JsonNode second = liked(me, "size=2&cursor=" + cursor);
        assertEquals(List.of("셋째 책"), titles(second));
        assertEquals(2, second.path("items").path(0).path("people").path(0).path("likedCount").asInt());
        assertTrue(second.path("nextCursor").isNull());
    }

    @Test
    @DisplayName("FR-09 좋아요한 감상이 없으면 빈 목록이고 내 감상에 누른 좋아요는 mine으로 표시한다")
    void emptyAndMine() throws Exception {
        User me = user();
        assertEquals(0, liked(me, "").path("items").size());

        UserBook mine = book(me);
        like(me, review(me, mine, Visibility.PUBLIC, MONDAY), 1);
        assertTrue(liked(me, "").path("items").path(0).path("people").path(0).path("mine").asBoolean());
    }

    @Test
    @DisplayName("FR-09 size가 1~50 밖이거나 cursor가 잘못되면 400이고 토큰이 없으면 401이다")
    void rejectsInvalidRequests() throws Exception {
        User me = user();
        for (String query : List.of("size=0", "size=51", "size=abc", "cursor=!!!", "cursor=bm90LWEtZGF0ZQ"))
            request(get("/api/feed/liked-books?" + query), me, null, 400);
        request(get("/api/feed/liked-books"), null, null, 401);
    }

    private void like(User user, Review review, int secondsAfterMonday) {
        likes.save(
                new ReviewLike(
                        new ReviewLikeId(review.getId(), user.getId()),
                        MONDAY.plusSeconds(secondsAfterMonday)));
    }

    private JsonNode liked(User user, String query) throws Exception {
        return request(get("/api/feed/liked-books?" + query), user, null, 200);
    }

    private static List<String> titles(JsonNode page) {
        return list(page.path("items")).stream().map(item -> item.path("bookTitle").asString()).toList();
    }
}
