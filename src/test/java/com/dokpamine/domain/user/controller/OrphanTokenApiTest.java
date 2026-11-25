package com.dokpamine.domain.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.time.Instant;
import java.util.UUID;

class OrphanTokenApiTest extends ApiTestSupport {
    @Autowired JwtEncoder encoder;

    @Test
    @DisplayName("FR-10 DB에 없는 사용자의 토큰은 404·500이 아니라 401로 막아 앱이 다시 로그인하게 한다")
    void tokenOfMissingUserIsUnauthorized() throws Exception {
        String token = token(UUID.randomUUID().toString());
        UUID bookId = UUID.randomUUID();
        long count = books.count();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sync/snapshot").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        put("/api/books/{id}", bookId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {"id":"%s","title":"데미안","author":"헤르만 헤세","totalPages":240,
                                         "genre":"기타","status":"READING","currentPage":0,"completedCount":0,
                                         "createdAt":"2026-10-26T00:00:00Z","updatedAt":"2026-10-26T00:00:00Z"}
                                        """
                                                .formatted(bookId)))
                .andExpect(status().isUnauthorized());
        assertEquals(count, books.count());
    }

    @Test
    @DisplayName("FR-10 UUID가 아닌 subject의 토큰도 500이 아니라 401이다")
    void tokenWithInvalidSubjectIsUnauthorized() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token("not-a-uuid")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-10 있는 사용자의 정상 토큰은 그대로 통과한다")
    void tokenOfExistingUserStillWorks() throws Exception {
        User reader = user();
        mvc.perform(
                        get("/api/me")
                                .header("Authorization", "Bearer " + token(reader.getId().toString())))
                .andExpect(status().isOk());
    }

    private String token(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject(subject)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(600))
                        .build();
        return encoder.encode(
                        JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
