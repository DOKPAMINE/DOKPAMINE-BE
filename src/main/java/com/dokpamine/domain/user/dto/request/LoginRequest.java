package com.dokpamine.domain.user.dto.request;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotNull(message = "필수 값을 입력해 주세요.") String email,
        @NotNull(message = "필수 값을 입력해 주세요.") String password) {}
