package com.dokpamine.domain.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;

import tools.jackson.databind.JsonNode;

import java.util.*;

class AuthApiTest extends ApiTestSupport {
    @Test
    @DisplayName("FR-10 회원가입 토큰으로 내 정보를 조회하고 로그인한다")
    void signupAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        JsonNode created =
                request(
                        post("/api/auth/signup"),
                        null,
                        Map.of(
                                "email",
                                email.toUpperCase(Locale.ROOT),
                                "password",
                                "password1",
                                "nickname",
                                "독자"),
                        201);
        String token = created.path("accessToken").asString();
        assertEquals(email, created.path("user").path("email").asString());
        assertFalse(created.path("user").path("rankingOptIn").asBoolean());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode login =
                request(
                        post("/api/auth/login"),
                        null,
                        Map.of("email", email, "password", "password1"),
                        200);
        assertEquals(created.path("user").path("id"), login.path("user").path("id"));
        request(
                post("/api/auth/signup"),
                null,
                Map.of("email", email, "password", "password1", "nickname", "독자"),
                409);
        assertEquals(
                "INVALID_CREDENTIALS",
                request(
                                post("/api/auth/login"),
                                null,
                                Map.of("email", email, "password", "wrong"),
                                401)
                        .path("code")
                        .asString());
    }

    @Test
    @DisplayName("FR-10 한글 비밀번호가 72바이트를 넘으면 400을 반환하고 정확히 72바이트면 가입한다")
    void validatesSignupPasswordByteLength() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        JsonNode error =
                request(
                        post("/api/auth/signup"),
                        null,
                        Map.of("email", email, "password", "가".repeat(25), "nickname", "독자"),
                        400);
        assertEquals("VALIDATION_FAILED", error.path("code").asString());
        assertEquals(
                "password: 비밀번호를 UTF-8 기준 72바이트 이하로 입력해 주세요.",
                error.path("message").asString());

        request(
                post("/api/auth/signup"),
                null,
                Map.of("email", email, "password", "가".repeat(24), "nickname", "독자"),
                201);
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
                Map.of("email", "invalid", "password", "short", "nickname", ""),
                400);
        request(get("/api/me"), null, null, 401);
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        request(get("/actuator/health"), null, null, 200);
    }
}
