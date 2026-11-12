package com.dokpamine.domain.user.dto.request;

import com.dokpamine.global.util.TextValues;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 주소를 입력해 주세요.")
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 255) String email,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호를 8~64자로 입력해 주세요.")
        @BcryptPasswordLength String password,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @Size(min = 1, max = 30, message = "닉네임을 1~30자로 입력해 주세요.") String nickname) {
    public SignupRequest {
        email = TextValues.trim(email);
        nickname = TextValues.trim(nickname);
    }
}
