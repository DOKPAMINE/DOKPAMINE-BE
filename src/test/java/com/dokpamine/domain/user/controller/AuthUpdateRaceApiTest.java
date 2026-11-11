package com.dokpamine.domain.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.record.entity.SessionSource;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:profile-update-race;"
        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AuthUpdateRaceApiTest extends ApiTestSupport {
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanCommittedData() {
        sessions.deleteAll();
        books.deleteAll();
        users.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("FR-15 동시 프로필 수정은 커밋 순서와 무관하게 닉네임과 랭킹 참여 해제를 보존한다")
    void preservesNicknameAndOptOut(boolean optOutCommitsFirst) throws Exception {
        User owner = user("원래 이름", true);
        User viewer = user("조회자", true);
        session(owner, 600, MONDAY, SessionSource.TIMER, false);
        session(viewer, 60, MONDAY, SessionSource.TIMER, false);
        assertEquals(2, ranking(viewer).path("top").size());

        CountDownLatch staleRead = new CountDownLatch(1);
        CountDownLatch firstCommitted = new CountDownLatch(1);
        Map<String, Object> nickname = Map.of("nickname", "새 이름");
        Map<String, Object> optOut = Map.of("rankingOptIn", false);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                // FR-15: 후행 요청의 영속성 컨텍스트에 이전 상태를 유지한 채 선행 요청을 커밋한다.
                User loaded = em.find(User.class, owner.getId());
                assertTrue(loaded.getRankingOptIn());
                assertEquals("원래 이름", loaded.getNickname());
                staleRead.countDown();
                try {
                    assertTrue(firstCommitted.await(10, TimeUnit.SECONDS), "선행 요청이 커밋되지 않았습니다.");
                    patchProfile(owner, optOutCommitsFirst ? nickname : optOut);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            try {
                assertTrue(staleRead.await(10, TimeUnit.SECONDS), "후행 요청이 사용자를 읽지 못했습니다.");
                patchProfile(owner, optOutCommitsFirst ? optOut : nickname);
            } finally {
                firstCommitted.countDown();
            }
            second.get(10, TimeUnit.SECONDS);
        }

        User saved = users.findById(owner.getId()).orElseThrow();
        assertEquals("새 이름", saved.getNickname());
        assertFalse(saved.getRankingOptIn());
        JsonNode publicRanking = ranking(viewer);
        assertEquals(1, publicRanking.path("top").size());
        assertEquals(viewer.getId().toString(), publicRanking.path("top").path(0).path("userId").asString());
        JsonNode ownRanking = ranking(owner);
        assertFalse(ownRanking.path("participating").asBoolean());
        assertTrue(ownRanking.path("around").isEmpty());
    }

    private void patchProfile(User owner, Map<String, Object> body) throws Exception {
        mvc.perform(patch("/api/me")
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private JsonNode ranking(User viewer) throws Exception {
        var response = mvc.perform(get("/api/ranking/weekly?weekStart=2026-10-26")
                        .with(jwt().jwt(token -> token.subject(viewer.getId().toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }
}
