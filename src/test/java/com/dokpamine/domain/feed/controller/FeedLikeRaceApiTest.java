package com.dokpamine.domain.feed.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.feed.entity.ReviewLikeId;
import com.dokpamine.domain.feed.repository.ReviewLikeRepository;
import com.dokpamine.domain.record.entity.Visibility;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.JsonNode;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.url=${dokpamine.test.postgresql.url:"
        + "jdbc:h2:mem:feed-like-race;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}")
class FeedLikeRaceApiTest extends ApiTestSupport {
    @MockitoSpyBean private ReviewLikeRepository likes;

    @Test
    @DisplayName("FR-09 같은 사용자의 동시 좋아요 요청은 모두 성공하고 한 건만 남는다")
    void concurrentLikesAreIdempotent() throws Exception {
        User owner = user(), reader = user();
        var review = review(owner, book(owner), Visibility.PUBLIC, MONDAY);
        String path = "/api/feed/" + review.getId() + "/like";
        try {
            CountDownLatch ready = new CountDownLatch(2);
            Set<Object> transactions = ConcurrentHashMap.newKeySet();
            // 실제 INSERT 직전에만 두 요청을 맞추고 DB 쿼리와 커밋은 그대로 실행한다.
            doAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                transactions.add(em.getDelegate());
                ready.countDown();
                assertTrue(ready.await(10, TimeUnit.SECONDS), "두 요청이 저장에 도달하지 않았습니다.");
                return mockingDetails(likes).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            }).when(likes).insertIfAbsent(any(UUID.class), any(UUID.class));

            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> postLike(path, reader));
                var second = executor.submit(() -> postLike(path, reader));
                for (var response : java.util.List.of(first, second)) {
                    JsonNode body = response.get(15, TimeUnit.SECONDS);
                    assertEquals(1, body.path("likeCount").asLong());
                    assertTrue(body.path("likedByMe").asBoolean());
                }
            }
            assertEquals(2, transactions.size(), "서로 다른 트랜잭션의 영속성 컨텍스트여야 합니다.");
            assertEquals(1, likes.countByIdReviewId(review.getId()));
            assertTrue(likes.existsById(new ReviewLikeId(review.getId(), reader.getId())));

            for (int i = 0; i < 2; i++) {
                var response = mvc.perform(delete(path)
                                .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                        .andExpect(status().isOk()).andReturn().getResponse();
                JsonNode body = json.readTree(response.getContentAsByteArray());
                assertEquals(0, body.path("likeCount").asLong());
                assertFalse(body.path("likedByMe").asBoolean());
            }
            assertEquals(0, likes.countByIdReviewId(review.getId()));
        } finally {
            // PostgreSQL CI는 다른 통합 테스트와 DB를 공유하므로 커밋한 데이터도 정리한다.
            likes.deleteById(new ReviewLikeId(review.getId(), reader.getId()));
            reviews.deleteById(review.getId());
            books.deleteById(review.getBookId());
            users.deleteById(reader.getId());
            users.deleteById(owner.getId());
        }
    }

    private JsonNode postLike(String path, User reader) throws Exception {
        var response = mvc.perform(post(path)
                        .with(jwt().jwt(token -> token.subject(reader.getId().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }
}
