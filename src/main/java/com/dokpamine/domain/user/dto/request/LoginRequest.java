package com.dokpamine.domain.user.dto.request;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotNull String email, @NotNull String password) {}
