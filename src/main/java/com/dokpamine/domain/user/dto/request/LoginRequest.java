package com.dokpamine.domain.user.dto.request;

import com.dokpamine.global.util.TextValues;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.*;

public record LoginRequest(
        // 구버전 앱도 기존 계정으로 로그인할 수 있도록 요청 필드를 호환한다.
        @JsonAlias("email")
        @NotNull(message = "아이디를 입력해 주세요.")
        @Size(max = 255, message = "아이디를 255자 이하로 입력해 주세요.") String username,
        @NotNull(message = "필수 값을 입력해 주세요.") String password) {
    public LoginRequest {
        username = TextValues.trimToNull(username);
    }
}
