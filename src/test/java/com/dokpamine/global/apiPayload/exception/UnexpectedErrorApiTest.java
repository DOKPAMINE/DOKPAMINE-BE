package com.dokpamine.global.apiPayload.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.ranking.service.RankingService;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@ExtendWith(OutputCaptureExtension.class)
class UnexpectedErrorApiTest extends ApiTestSupport {
    @MockitoBean RankingService ranking;

    @Test
    @DisplayName("NFR-09 예상하지 못한 서버 오류는 내부 메시지 없이 500 INTERNAL_ERROR로 안내하고 원인은 로그에 남긴다")
    void unexpectedErrorIsInternalError(CapturedOutput output) throws Exception {
        when(ranking.weekly(any(), any())).thenThrow(new IllegalStateException("내부 상세 secret-detail"));

        JsonNode error = request(get("/api/ranking/weekly"), user(), null, 500);
        assertEquals("INTERNAL_ERROR", error.path("code").asString());
        assertEquals("요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.", error.path("message").asString());
        assertFalse(error.toString().contains("secret-detail"));
        assertTrue(output.getAll().contains("secret-detail"));
    }

    @Test
    @DisplayName("NFR-09 지원하지 않는 요청 방식은 405 METHOD_NOT_ALLOWED로 안내한다")
    void methodNotAllowed() throws Exception {
        String body =
                mvc.perform(
                                delete("/api/catalog/genres")
                                        .with(jwt().jwt(token -> token.subject(user().getId().toString()))))
                        .andExpect(status().isMethodNotAllowed())
                        .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        JsonNode error = json.readTree(body);
        assertEquals("METHOD_NOT_ALLOWED", error.path("code").asString());
        assertEquals("지원하지 않는 요청 방식이에요.", error.path("message").asString());
    }

    @Test
    @DisplayName("NFR-05 JSON이 아닌 형식으로 기록을 보내면 415 UNSUPPORTED_MEDIA_TYPE으로 안내한다")
    void unsupportedMediaType() throws Exception {
        User owner = user();
        UUID id = UUID.randomUUID();
        String body =
                mvc.perform(
                                put("/api/books/{id}", id)
                                        .with(jwt().jwt(token -> token.subject(owner.getId().toString())))
                                        .contentType(TEXT_PLAIN)
                                        .content("데미안"))
                        .andExpect(status().isUnsupportedMediaType())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        JsonNode error = json.readTree(body);
        assertEquals("UNSUPPORTED_MEDIA_TYPE", error.path("code").asString());
        assertEquals("요청 형식을 확인해 주세요.", error.path("message").asString());
    }

    @Test
    @DisplayName("FR-10 토큰 없이 가입 API에 JSON이 아닌 형식을 보내도 401이 아니라 415로 안내한다")
    void unsupportedMediaTypeWithoutToken() throws Exception {
        String body =
                mvc.perform(post("/api/auth/signup").contentType(TEXT_PLAIN).content("가입"))
                        .andExpect(status().isUnsupportedMediaType())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("UNSUPPORTED_MEDIA_TYPE", json.readTree(body).path("code").asString());
    }

    @Test
    @DisplayName("FR-10 토큰이 없으면 기존처럼 401이다")
    void unauthenticatedStillUnauthorized() throws Exception {
        mvc.perform(get("/api/ranking/weekly")).andExpect(status().isUnauthorized());
    }
}
