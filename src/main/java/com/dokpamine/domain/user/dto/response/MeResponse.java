package com.dokpamine.domain.user.dto.response;

import com.dokpamine.domain.user.entity.User;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record MeResponse(UUID id, String email, String nickname, boolean rankingOptIn) {
    public MeResponse(User user) {
        this(user.getId(), user.getEmail(), user.getNickname(), user.getRankingOptIn());
    }
}
