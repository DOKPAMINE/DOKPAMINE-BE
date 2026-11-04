package com.dokpamine.domain.user.dto.response;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record TokenResponse(String accessToken, Instant expiresAt, MeResponse user) {}
