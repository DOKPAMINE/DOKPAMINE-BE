package com.dokpamine.global.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class SwaggerApiTest extends ApiTestSupport {
    private static final Map<String, List<String>> OPERATIONS =
            Map.ofEntries(
                    Map.entry("/api/auth/signup", List.of("post")),
                    Map.entry("/api/auth/login", List.of("post")),
                    Map.entry("/api/me", List.of("get", "patch")),
                    Map.entry("/api/books/{id}", List.of("put")),
                    Map.entry("/api/sessions/{id}", List.of("put")),
                    Map.entry("/api/reviews/{id}", List.of("put")),
                    Map.entry("/api/quotes/{id}", List.of("put")),
                    Map.entry("/api/sync/snapshot", List.of("get")),
                    Map.entry("/api/catalog/isbn/{isbn}", List.of("get")),
                    Map.entry("/api/catalog/genres", List.of("get")),
                    Map.entry("/api/feed", List.of("get")),
                    Map.entry("/api/feed/{id}", List.of("get")),
                    Map.entry("/api/feed/{id}/like", List.of("post", "delete")),
                    Map.entry("/api/feed/books", List.of("get")),
                    Map.entry("/api/ranking/weekly", List.of("get")));
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/signup", "/api/auth/login");
    private static final Pattern KOREAN = Pattern.compile("[가-힣]");

    private JsonNode apiDocs() throws Exception {
        return request(get("/v3/api-docs"), null, null, 200);
    }

    private void assertResponseSchema(JsonNode response, String schemaName) {
        assertFalse(response.isMissingNode());
        assertTrue(
                list(response.path("content")).stream()
                        .anyMatch(media ->
                                media.path("schema")
                                        .path("$ref")
                                        .asString()
                                        .endsWith("/" + schemaName)),
                "응답 스키마: " + schemaName);
    }

    @Test
    @DisplayName("FR-10 Swagger 문서와 UI는 인증 없이 열리고 JWT Bearer 인증을 안내한다")
    void publicDocumentationAndUi() throws Exception {
        JsonNode docs = apiDocs();
        assertTrue(docs.path("openapi").asString().startsWith("3."));
        assertFalse(docs.path("info").path("title").asString().isBlank());
        JsonNode bearer = docs.path("components").path("securitySchemes").path("BearerAuth");
        assertEquals("http", bearer.path("type").asString());
        assertEquals("bearer", bearer.path("scheme").asString());
        assertEquals("JWT", bearer.path("bearerFormat").asString());
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-01 FR-08 FR-10 FR-15 생성 명세에 기존 API 17개와 한국어 설명이 포함된다")
    void allBusinessOperationsHaveDescriptions() throws Exception {
        JsonNode paths = apiDocs().path("paths");
        int documented = 0;
        for (var entry : OPERATIONS.entrySet()) {
            for (String method : entry.getValue()) {
                JsonNode operation = paths.path(entry.getKey()).path(method);
                String route = method.toUpperCase() + " " + entry.getKey();
                assertFalse(operation.isMissingNode(), route);
                assertTrue(KOREAN.matcher(operation.path("summary").asString()).find(), route);
                assertFalse(operation.path("tags").isEmpty(), route);
                documented++;
            }
        }
        assertEquals(17, documented);
    }

    @Test
    @DisplayName("FR-10 공개 인증 API와 JWT가 필요한 API의 문서 권한이 구분된다")
    void authenticationRequirementsMatchApiContract() throws Exception {
        JsonNode docs = apiDocs();
        assertTrue(docs.path("security").isMissingNode() || docs.path("security").isEmpty());
        for (var entry : OPERATIONS.entrySet()) {
            for (String method : entry.getValue()) {
                JsonNode security =
                        docs.path("paths").path(entry.getKey()).path(method).path("security");
                if (PUBLIC_PATHS.contains(entry.getKey())) {
                    assertTrue(security.isMissingNode() || security.isEmpty(), entry.getKey());
                } else {
                    assertTrue(
                            list(security).stream().anyMatch(item -> item.has("BearerAuth")),
                            entry.getKey());
                }
            }
        }
    }

    @Test
    @DisplayName("FR-10 Swagger를 공개해도 기존 업무 API는 토큰 없는 요청을 거절한다")
    void businessAuthenticationRemainsRequired() throws Exception {
        for (var entry : OPERATIONS.entrySet()) {
            if (PUBLIC_PATHS.contains(entry.getKey())) continue;
            String url =
                    entry.getKey()
                            .replace("{id}", "00000000-0000-0000-0000-000000000001")
                            .replace("{isbn}", "9788937460449");
            for (String method : entry.getValue()) {
                mvc.perform(
                                MockMvcRequestBuilders.request(
                                        HttpMethod.valueOf(method.toUpperCase()), url))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    @DisplayName("FR-10 명세는 실제 응답 상태와 DTO를 유지하고 서버 인증 객체를 숨긴다")
    void responseContractAndHiddenAuthentication() throws Exception {
        JsonNode docs = apiDocs();
        JsonNode signup = docs.path("paths").path("/api/auth/signup").path("post");
        assertResponseSchema(signup.path("responses").path("201"), "TokenResponse");
        assertResponseSchema(signup.path("responses").path("400"), "ErrorResponse");
        assertResponseSchema(signup.path("responses").path("409"), "ErrorResponse");
        assertResponseSchema(
                docs.path("paths")
                        .path("/api/auth/login")
                        .path("post")
                        .path("responses")
                        .path("401"),
                "ErrorResponse");
        assertResponseSchema(
                docs.path("paths")
                        .path("/api/books/{id}")
                        .path("put")
                        .path("responses")
                        .path("403"),
                "ErrorResponse");
        JsonNode unauthorized =
                docs.path("paths").path("/api/me").path("get").path("responses").path("401");
        assertFalse(unauthorized.isMissingNode());
        assertTrue(
                unauthorized.path("content").isMissingNode()
                        || unauthorized.path("content").isEmpty());
        for (var entry : OPERATIONS.entrySet()) {
            for (String method : entry.getValue()) {
                JsonNode operation = docs.path("paths").path(entry.getKey()).path(method);
                for (JsonNode parameter : operation.path("parameters")) {
                    assertNotEquals("auth", parameter.path("name").asString(), entry.getKey());
                    assertFalse(
                            parameter
                                    .path("schema")
                                    .path("$ref")
                                    .asString()
                                    .endsWith("/Authentication"));
                }
            }
        }
    }
}
