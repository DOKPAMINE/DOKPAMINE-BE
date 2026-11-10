package com.dokpamine.domain.user.dto.request;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotNull @Email @Size(max = 255) String email,
        @NotNull @Size(min = 8, max = 64) @BcryptPasswordLength String password,
        @NotNull @Size(min = 1, max = 30) String nickname) {}
