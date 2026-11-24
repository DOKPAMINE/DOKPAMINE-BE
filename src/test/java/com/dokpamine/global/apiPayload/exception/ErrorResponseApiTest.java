package com.dokpamine.global.apiPayload.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.support.ApiTestSupport;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
