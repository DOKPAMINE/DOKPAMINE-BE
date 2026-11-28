package com.dokpamine.domain.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.record.entity.SessionSource;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.domain.user.repository.UserRepository;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:profile-update-race;"
        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AuthUpdateRaceApiTest extends ApiTestSupport {
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private UserRepository userRepository;

    @AfterEach
    void cleanCommittedData() {
        reset(userRepository);
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

    @Test
    @DisplayName("FR-10 두 사용자가 동시에 같은 닉네임으로 바꾸면 한 명만 바뀌고 다른 요청은 409 NICKNAME_TAKEN을 반환한다")
    void rejectsConcurrentDuplicateNickname() throws Exception {
        User first = user("첫째", false);
        User second = user("둘째", false);
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        var repositoryQuery = mockingDetails(userRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            boolean exists = (boolean) repositoryQuery.answer(invocation);
            assertFalse(exists);
            // FR-10: 두 요청이 실제 DB 닉네임 조회를 통과한 뒤에 저장하도록 맞춘다.
            bothChecked.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(userRepository).existsByNicknameAndIdNot(eq("같은 이름"), any());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = List.of(first, second).stream()
                    .map(user -> executor.submit(() -> mvc.perform(patch("/api/me")
                                    .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                                    .contentType(APPLICATION_JSON)
                                    .content(json.writeValueAsString(Map.of("nickname", "같은 이름"))))
                            .andReturn().getResponse()))
                    .toList();
            var responses = List.of(requests.get(0).get(20, TimeUnit.SECONDS),
                    requests.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), responses.stream().map(r -> r.getStatus()).sorted().toList());
            var conflict = responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
            assertEquals("NICKNAME_TAKEN",
                    json.readTree(conflict.getContentAsByteArray()).path("code").asString());
        }
        var nicknames = List.of(users.findById(first.getId()).orElseThrow().getNickname(),
                users.findById(second.getId()).orElseThrow().getNickname());
        assertEquals(1, nicknames.stream().filter("같은 이름"::equals).count());
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
