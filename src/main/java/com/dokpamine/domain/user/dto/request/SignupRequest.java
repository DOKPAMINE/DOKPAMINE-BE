package com.dokpamine.domain.user.dto.request;

import com.dokpamine.global.util.TextValues;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotNull(message = "아이디를 입력해 주세요.")
        @Pattern(regexp = "[A-Za-z0-9_]{4,30}", message = "아이디는 영문, 숫자, 밑줄을 사용해 4~30자로 입력해 주세요.") String username,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호를 8~64자로 입력해 주세요.")
        @BcryptPasswordLength String password,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @Size(min = 1, max = 15, message = "닉네임을 1~15자로 입력해 주세요.") String nickname) {
    public SignupRequest {
        username = TextValues.trimToNull(username);
        nickname = TextValues.trim(nickname);
    }
}
