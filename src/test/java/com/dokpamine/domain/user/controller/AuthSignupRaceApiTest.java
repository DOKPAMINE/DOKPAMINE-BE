package com.dokpamine.domain.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.user.dto.request.SignupRequest;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.domain.user.repository.UserRepository;
import com.dokpamine.domain.user.service.AuthService;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.url=${dokpamine.test.postgresql.url:"
        + "jdbc:h2:mem:auth-signup-race;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}")
class AuthSignupRaceApiTest extends ApiTestSupport {
    @MockitoSpyBean private UserRepository userRepository;
    @Autowired private AuthService authService;
    private final String username = UUID.randomUUID().toString().substring(0, 8);
    private final String otherUsername = UUID.randomUUID().toString().substring(0, 8);
    private final String nickname = "독자" + username;

    @AfterEach
    void cleanCommittedData() {
        reset(userRepository);
        for (String value : List.of(username, otherUsername)) {
            User saved = users.findByUsername(value);
            if (saved != null) users.deleteById(saved.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("FR-10 같은 아이디로 동시에 가입하면 한 건만 저장하고 다른 요청은 409 USERNAME_TAKEN을 반환한다")
    void preservesUsernameConflictContract(boolean normalizeUsername) throws Exception {
        long originalCount = users.count();
        String contenderUsername = normalizeUsername
                ? "\u00a0 " + username.toUpperCase(Locale.ROOT) + "\u3000" : username;
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        var repositoryQuery = mockingDetails(userRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            boolean exists = (boolean) repositoryQuery.answer(invocation);
            assertFalse(exists);
            // FR-10: 두 요청이 실제 DB 중복 조회를 통과한 뒤에 저장하도록 맞춘다.
            bothChecked.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(userRepository).existsByUsername(username);

        try (var executor = Executors.newFixedThreadPool(2)) {
            // 닉네임은 서로 달라야 아이디 UNIQUE 충돌만 검증한다.
            var first = executor.submit(() -> signup(username, nickname));
            var second = executor.submit(() -> signup(contenderUsername, "독자" + otherUsername));
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), responses.stream().map(r -> r.getStatus()).sorted().toList());
            var conflict = responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
            var error = json.readTree(conflict.getContentAsByteArray());
            assertEquals("USERNAME_TAKEN", error.path("code").asString());
            assertEquals("이미 사용 중인 아이디예요.", error.path("message").asString());
            assertFalse(error.has("accessToken"));
            var created = json.readTree(responses.stream().filter(r -> r.getStatus() == 201)
                    .findFirst().orElseThrow().getContentAsByteArray());
            assertEquals(originalCount + 1, users.count());
            User saved = users.findByUsername(username);
            assertEquals(saved.getId().toString(), created.path("user").path("id").asString());
            mvc.perform(get("/api/me").header("Authorization", "Bearer "
                            + created.path("accessToken").asString()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("FR-10 UNIQUE 이외의 저장 오류는 USERNAME_TAKEN으로 변환하지 않는다")
    void preservesOtherIntegrityFailures() {
        var failure = new DataIntegrityViolationException("길이 초과", new SQLException("길이 초과", "22001"));
        doThrow(failure).when(userRepository).saveAndFlush(any(User.class));
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> authService.signup(new SignupRequest(username, "password1", nickname))));
        assertFalse(users.existsByUsername(username));
    }

    @Test
    @DisplayName("FR-10 같은 닉네임으로 동시에 가입하면 한 건만 저장하고 다른 요청은 409 NICKNAME_TAKEN을 반환한다")
    void preservesNicknameConflictContract() throws Exception {
        long originalCount = users.count();
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        var repositoryQuery = mockingDetails(userRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            boolean exists = (boolean) repositoryQuery.answer(invocation);
            assertFalse(exists);
            // FR-10: 두 요청이 실제 DB 닉네임 조회를 통과한 뒤에 저장하도록 맞춘다.
            bothChecked.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(userRepository).existsByNickname(nickname);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> signup(username, nickname));
            var second = executor.submit(() -> signup(otherUsername, " " + nickname + " "));
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), responses.stream().map(r -> r.getStatus()).sorted().toList());
            var conflict = responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
            var error = json.readTree(conflict.getContentAsByteArray());
            assertEquals("NICKNAME_TAKEN", error.path("code").asString());
            assertEquals("이미 사용 중인 닉네임이에요.", error.path("message").asString());
            assertFalse(error.has("accessToken"));
            assertEquals(originalCount + 1, users.count());
        }
    }

    private org.springframework.mock.web.MockHttpServletResponse signup(String username, String nickname)
            throws Exception {
        return mvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", username, "password", "password1", "nickname", nickname))))
                .andReturn().getResponse();
    }
}
