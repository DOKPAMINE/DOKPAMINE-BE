package com.dokpamine.global.apiPayload.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

class ErrorResponseApiTest extends ApiTestSupport {
    @Test
    @DisplayName("FR-10 토큰 없는 요청의 오류 전달도 원래 상태 코드를 401로 가리지 않는다")
    void errorDispatchKeepsOriginalStatusWithoutToken() throws Exception {
        mvc.perform(
                        get("/error")
                                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 415)
                                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/auth/signup"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("FR-10 잘못된 JSON으로 가입·로그인하면 401이 아니라 400 BAD_REQUEST로 안내한다")
    void malformedAuthBodyIsBadRequest() throws Exception {
        for (String path : new String[] {"/api/auth/signup", "/api/auth/login"}) {
            JsonNode error = raw(post(path), null, "{\"email\":", 400);
            assertEquals("BAD_REQUEST", error.path("code").asString());
            assertEquals("요청 형식을 확인해 주세요.", error.path("message").asString());
        }
    }

    @Test
    @DisplayName("NFR-05 형식이 맞지 않는 기록 필드는 400 BAD_REQUEST로 안내하고 저장하지 않는다")
    void unreadableRecordFieldIsBadRequest() throws Exception {
        User owner = user();
        UUID id = UUID.randomUUID();
        long count = books.count();
        JsonNode error =
                raw(
                        put("/api/books/{id}", id),
                        owner,
                        """
                        {"id":"%s","title":"데미안","author":"헤르만 헤세","totalPages":240,
                         "genre":"기타","status":"READING","currentPage":0,"completedCount":0,
                         "deleted":null,"createdAt":"2026-10-26T00:00:00Z","updatedAt":"2026-10-26T00:00:00Z"}
                        """
                                .formatted(id),
                        400);
        assertEquals("BAD_REQUEST", error.path("code").asString());
        assertEquals("요청 형식을 확인해 주세요.", error.path("message").asString());
        assertEquals(count, books.count());
    }

    @Test
    @DisplayName("FR-08 FR-15 경로·쿼리 값의 형식 오류는 400 BAD_REQUEST로 안내한다")
    void typeMismatchIsBadRequest() throws Exception {
        User reader = user();
        for (MockHttpServletRequestBuilder request :
                new MockHttpServletRequestBuilder[] {
                    get("/api/feed/not-a-uuid"),
                    get("/api/feed/user-books/not-a-uuid"),
                    get("/api/ranking/weekly").param("weekStart", "bad")
                }) {
            JsonNode error = request(request, reader, null, 400);
            assertEquals("BAD_REQUEST", error.path("code").asString());
            assertEquals("요청 값의 형식을 확인해 주세요.", error.path("message").asString());
        }
    }

    @Test
    @DisplayName("NFR-09 없는 경로는 404 NOT_FOUND로 안내하고 토큰 없는 요청은 그대로 401이다")
    void unknownPathIsNotFound() throws Exception {
        JsonNode error = request(get("/api/nothing"), user(), null, 404);
        assertEquals("NOT_FOUND", error.path("code").asString());
        assertEquals("요청한 주소를 찾을 수 없어요.", error.path("message").asString());
        request(get("/api/nothing"), null, null, 401);
    }

    private JsonNode raw(MockHttpServletRequestBuilder request, User user, String body, int expected)
            throws Exception {
        if (user != null) request.with(jwt().jwt(token -> token.subject(user.getId().toString())));
        String value =
                mvc.perform(request.contentType(APPLICATION_JSON).content(body))
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(value);
    }
}
