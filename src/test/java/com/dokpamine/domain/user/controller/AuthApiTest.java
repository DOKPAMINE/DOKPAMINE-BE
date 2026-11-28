package com.dokpamine.domain.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.JsonNode;

import java.util.*;

class AuthApiTest extends ApiTestSupport {
    @Autowired private PasswordEncoder passwords;

    @Test
    @DisplayName("FR-10 회원가입 토큰으로 내 정보를 조회하고 로그인한다")
    void signupAndLogin() throws Exception {
        String username = UUID.randomUUID().toString().substring(0, 8);
        JsonNode created =
                request(
                        post("/api/auth/signup"),
                        null,
                        Map.of(
                                "username",
                                username.toUpperCase(Locale.ROOT),
                                "password",
                                "password1",
                                "nickname",
                                "독자"),
                        201);
        String token = created.path("accessToken").asString();
        assertEquals(username, created.path("user").path("username").asString());
        assertFalse(created.path("user").path("rankingOptIn").asBoolean());
        var me = json.readTree(mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertEquals(username, me.path("username").asString());
        JsonNode login =
                request(
                        post("/api/auth/login"),
                        null,
                        Map.of("username", username, "password", "password1"),
                        200);
        assertEquals(created.path("user").path("id"), login.path("user").path("id"));
        var duplicate = request(post("/api/auth/signup"), null,
                Map.of("username", username, "password", "password1", "nickname", "독자"), 409);
        assertEquals("USERNAME_TAKEN", duplicate.path("code").asString());
        assertEquals(
                "INVALID_CREDENTIALS",
                request(
                                post("/api/auth/login"),
                                null,
                                Map.of("username", username, "password", "wrong"),
                                401)
                        .path("code")
                        .asString());
    }

    @Test
    @DisplayName("FR-10 기존 이메일 계정은 새 앱과 구버전 앱에서 같은 사용자로 로그인한다")
    void logsInWithLegacyEmail() throws Exception {
        String email = "reader." + UUID.randomUUID() + "@test.com";
        UUID id = UUID.randomUUID();
        users.saveAndFlush(new User(id, email, passwords.encode("password1"), "독자"));

        for (String field : List.of("username", "email")) {
            var login = request(post("/api/auth/login"), null,
                    Map.of(field, " " + email.toUpperCase(Locale.ROOT) + " ", "password", "password1"), 200);
            assertEquals(id.toString(), login.path("user").path("id").asString());
            assertEquals(email, login.path("user").path("username").asString());
        }
    }

    @Test
    @DisplayName("FR-10 한글 비밀번호가 72바이트를 넘으면 400을 반환하고 정확히 72바이트면 가입한다")
    void validatesSignupPasswordByteLength() throws Exception {
        String username = UUID.randomUUID().toString().substring(0, 8);
        JsonNode error =
                request(
                        post("/api/auth/signup"),
                        null,
                        Map.of("username", username, "password", "가".repeat(25), "nickname", "독자"),
                        400);
        assertEquals("VALIDATION_FAILED", error.path("code").asString());
        assertEquals(
                "비밀번호를 UTF-8 기준 72바이트 이하로 입력해 주세요.",
                error.path("message").asString());

        request(
                post("/api/auth/signup"),
                null,
                Map.of("username", username, "password", "가".repeat(24), "nickname", "독자"),
                201);
    }
    @Test
    @DisplayName("FR-10 정규화 후 빈 아이디과 닉네임은 가입을 거부한다")
    void rejectsBlankSignupFields() throws Exception {
        long count = users.count();
        for (String blank : List.of("", "   ", "\t\r\n", "\u00a0\u2003\u3000")) {
            for (String field : List.of("username", "nickname")) {
                Map<String, Object> body = new HashMap<>(Map.of(
                        "username", UUID.randomUUID().toString().substring(0, 8),
                        "password", "password1", "nickname", "독자"));
                body.put(field, blank);
                JsonNode error = request(post("/api/auth/signup"), null, body, 400);
                assertEquals("VALIDATION_FAILED", error.path("code").asString());
                assertEquals(count, users.count());
            }
        }
    }

    @Test
    @DisplayName("FR-10 가입과 프로필 수정은 닉네임 15자까지 허용하며 기존 긴 닉네임을 유지한다")
    void validatesNicknameLength() throws Exception {
        String username = UUID.randomUUID().toString().substring(0, 8);
        String limit = "가".repeat(15);
        String legacy = "가".repeat(16);
        var signupError = request(post("/api/auth/signup"), null,
                Map.of("username", username, "password", "password1", "nickname", legacy), 400);
        assertEquals("VALIDATION_FAILED", signupError.path("code").asString());
        var created = request(post("/api/auth/signup"), null,
                Map.of("username", username, "password", "password1", "nickname", " " + limit + " "), 201);
        assertEquals(limit, created.path("user").path("nickname").asString());

        var user = user(legacy, false);
        var unchanged = request(patch("/api/me"), user, Map.of("rankingOptIn", true), 200);
        assertEquals(legacy, unchanged.path("nickname").asString());
        var updateError = request(patch("/api/me"), user, Map.of("nickname", legacy), 400);
        assertEquals("VALIDATION_FAILED", updateError.path("code").asString());
        // FR-10: 가입한 사용자가 이미 쓰는 닉네임과 겹치지 않게 다른 15자 닉네임으로 수정한다.
        String otherLimit = "나".repeat(15);
        var changed = request(patch("/api/me"), user, Map.of("nickname", otherLimit), 200);
        assertEquals(otherLimit, changed.path("nickname").asString());
    }

    @Test
    @DisplayName("FR-10 정규화 후 빈 닉네임 수정은 거부하고 기존 프로필을 유지한다")
    void rejectsBlankProfileNickname() throws Exception {
        var user = user();
        em.flush();
        for (String blank : List.of("", "   ", "\t\r\n", "\u00a0\u2003\u3000")) {
            JsonNode error = request(patch("/api/me"), user,
                    Map.of("nickname", blank, "rankingOptIn", true), 400);
            assertEquals("VALIDATION_FAILED", error.path("code").asString());
            em.clear();
            var saved = users.findById(user.getId()).orElseThrow();
            assertEquals(user.getNickname(), saved.getNickname());
            assertFalse(saved.getRankingOptIn());
        }
    }

    @Test
    @DisplayName("FR-10 가입과 수정은 공백을 제거하고 닉네임 생략과 null은 기존 값을 유지한다")
    void normalizesUserFieldsAndPreservesOptionalNickname() throws Exception {
        String username = UUID.randomUUID().toString().substring(0, 8);
        JsonNode created = request(post("/api/auth/signup"), null,
                Map.of("username", "\u00a0 " + username.toUpperCase(Locale.ROOT) + "\u3000",
                        "password", "password1", "nickname", "\u00a0 독자 \u3000"), 201);
        assertEquals(username, created.path("user").path("username").asString());
        assertEquals("독자", created.path("user").path("nickname").asString());
        var user = users.findByUsername(username);
        JsonNode changed = request(patch("/api/me"), user,
                Map.of("nickname", "\u00a0 새 이름 \u3000"), 200);
        assertEquals("새 이름", changed.path("nickname").asString());
        Map<String, Object> nullNickname = new HashMap<>();
        nullNickname.put("nickname", null);
        nullNickname.put("rankingOptIn", false);
        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("rankingOptIn", true), nullNickname)) {
            JsonNode unchanged = request(patch("/api/me"), user, body, 200);
            assertEquals("새 이름", unchanged.path("nickname").asString());
            assertEquals(body.get("rankingOptIn"), unchanged.path("rankingOptIn").asBoolean());
        }
    }


    @Test
    @DisplayName("FR-10 개인정보 수정과 입력 검증 및 JWT 접근 제한을 유지한다")
    void updateAndValidate() throws Exception {
        var user = user();
        JsonNode changed =
                request(
                        patch("/api/me"),
                        user,
                        Map.of("nickname", "새 이름", "rankingOptIn", true),
                        200);
        assertEquals("새 이름", changed.path("nickname").asString());
        assertTrue(changed.path("rankingOptIn").asBoolean());
        request(patch("/api/me"), user, Map.of(), 200);
        request(
                post("/api/auth/signup"),
                null,
                Map.of("username", "invalid", "password", "short", "nickname", ""),
                400);
        request(get("/api/me"), null, null, 401);
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        request(get("/actuator/health"), null, null, 200);
    }

    @Test
    @DisplayName("FR-10 다른 사용자가 쓰는 닉네임으로 가입하면 앞뒤 공백과 무관하게 409 NICKNAME_TAKEN을 반환한다")
    void rejectsDuplicateSignupNickname() throws Exception {
        var owner = user();
        em.flush();
        long count = users.count();
        for (String nickname : List.of(owner.getNickname(), "\u00a0 " + owner.getNickname() + "\u3000")) {
            JsonNode error = request(post("/api/auth/signup"), null, Map.of(
                    "username", UUID.randomUUID().toString().substring(0, 8),
                    "password", "password1", "nickname", nickname), 409);
            assertEquals("NICKNAME_TAKEN", error.path("code").asString());
            assertEquals("이미 사용 중인 닉네임이에요.", error.path("message").asString());
            assertFalse(error.has("accessToken"));
            assertEquals(count, users.count());
        }
    }

    @Test
    @DisplayName("FR-10 아이디와 닉네임이 모두 겹치면 409 USERNAME_TAKEN을 반환한다")
    void prefersUsernameConflict() throws Exception {
        Map<String, Object> body = Map.of(
                "username", UUID.randomUUID().toString().substring(0, 8),
                "password", "password1", "nickname", "겹치는 이름");
        request(post("/api/auth/signup"), null, body, 201);
        JsonNode error = request(post("/api/auth/signup"), null, body, 409);
        assertEquals("USERNAME_TAKEN", error.path("code").asString());
    }

    @Test
    @DisplayName("FR-10 다른 사용자의 닉네임으로 수정하면 409를 반환하고 내 닉네임은 그대로 둔다")
    void rejectsDuplicateProfileNickname() throws Exception {
        var owner = user();
        var other = user();
        em.flush();
        JsonNode error = request(patch("/api/me"), other,
                Map.of("nickname", " " + owner.getNickname() + " ", "rankingOptIn", true), 409);
        assertEquals("NICKNAME_TAKEN", error.path("code").asString());
        assertEquals("이미 사용 중인 닉네임이에요.", error.path("message").asString());
        em.clear();
        var saved = users.findById(other.getId()).orElseThrow();
        assertEquals(other.getNickname(), saved.getNickname());
        assertFalse(saved.getRankingOptIn());
    }

    @Test
    @DisplayName("FR-10 내 현재 닉네임을 그대로 보내면 중복으로 보지 않고 200을 반환한다")
    void allowsOwnNickname() throws Exception {
        var user = user();
        em.flush();
        JsonNode changed = request(patch("/api/me"), user,
                Map.of("nickname", user.getNickname(), "rankingOptIn", true), 200);
        assertEquals(user.getNickname(), changed.path("nickname").asString());
        assertTrue(changed.path("rankingOptIn").asBoolean());
    }
}
