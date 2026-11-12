package com.dokpamine.domain.user.dto.request;

import com.dokpamine.global.util.TextValues;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") @Email @Size(max = 255) String email,
        @NotNull @Size(min = 8, max = 64) @BcryptPasswordLength String password,
        @NotNull @Size(min = 1, max = 30, message = "닉네임을 1~30자로 입력해 주세요.") String nickname) {
    public SignupRequest {
        email = TextValues.trim(email);
        nickname = TextValues.trim(nickname);
    }
}
