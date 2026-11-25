package com.dokpamine.domain.user.dto.response;

import com.dokpamine.domain.user.entity.User;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record MeResponse(UUID id, String username, String nickname, boolean rankingOptIn) {
    public MeResponse(User user) {
        this(user.getId(), user.getUsername(), user.getNickname(), user.getRankingOptIn());
    }
}
