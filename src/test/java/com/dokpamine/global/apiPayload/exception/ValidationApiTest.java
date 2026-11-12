package com.dokpamine.global.apiPayload.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

class ValidationApiTest extends ApiTestSupport {
    @ParameterizedTest
    @MethodSource("invalidAuthRequests")
    @DisplayName("FR-10 인증 입력 검증은 영어 언어 설정에도 한국어 존댓말 한 문장으로 안내한다")
    void validatesAuthInKorean(String endpoint, Map<String, Object> body, String message)
            throws Exception {
        var error = request(post(endpoint).header("Accept-Language", "en-US"), null, body, 400);
        assertEquals("VALIDATION_FAILED", error.path("code").asString());
        assertEquals(message, error.path("message").asString());
    }

    static Stream<Arguments> invalidAuthRequests() {
        return Stream.of(
                Arguments.of("/api/auth/login", Map.of(), "필수 값을 입력해 주세요."),
                Arguments.of("/api/auth/signup", signup("password", null), "필수 값을 입력해 주세요."),
                Arguments.of("/api/auth/signup", signup("email", "   "), "이메일을 입력해 주세요."),
                Arguments.of("/api/auth/signup", signup("email", "invalid"), "올바른 이메일 주소를 입력해 주세요."),
                Arguments.of("/api/auth/signup", signup("password", "short"), "비밀번호를 8~64자로 입력해 주세요."),
                Arguments.of("/api/auth/signup", signup("password", "가".repeat(25)), "비밀번호를 UTF-8 기준 72바이트 이하로 입력해 주세요."),
                Arguments.of("/api/auth/signup", signup("nickname", ""), "닉네임을 1~30자로 입력해 주세요."),
                Arguments.of("/api/auth/signup", Map.of("email", "invalid", "password", "short", "nickname", ""), "닉네임을 1~30자로 입력해 주세요."));
    }

    private static Map<String, Object> signup(String field, Object value) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "email", "reader@test.com", "password", "password1", "nickname", "독자"));
        body.put(field, value);
        return body;
    }

    @ParameterizedTest
    @MethodSource("invalidBookRequests")
    @DisplayName("NFR-05 기록 입력의 필수값 공백 길이 범위 검증은 한국어로 안내하고 저장하지 않는다")
    void validatesRecordInKorean(String field, Object value, String message) throws Exception {
        var owner = user();
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>(Map.of(
                "id", id, "title", "데미안", "author", "헤르만 헤세", "totalPages", 240,
                "genre", "소설", "status", "READING", "currentPage", 0, "completedCount", 0,
                "createdAt", MONDAY, "updatedAt", MONDAY));
        body.put(field, value);
        long count = books.count();
        var error = request(put("/api/books/{id}", id).header("Accept-Language", "en-US"),
                owner, body, 400);
        assertEquals("VALIDATION_FAILED", error.path("code").asString());
        assertEquals(message, error.path("message").asString());
        assertEquals(count, books.count());
    }

    static Stream<Arguments> invalidBookRequests() {
        return Stream.of(
                Arguments.of("id", null, "필수 값을 입력해 주세요."),
                Arguments.of("title", " ", "공백이 아닌 값을 입력해 주세요."),
                Arguments.of("title", "가".repeat(201), "200자 이하로 입력해 주세요."),
                Arguments.of("totalPages", 0, "1 이상의 값을 입력해 주세요."),
                Arguments.of("currentPage", -1, "0 이상의 값을 입력해 주세요."),
                Arguments.of("isbn", "invalid", "ISBN 은 10자리 또는 13자리 숫자여야 해요."));
    }
}
