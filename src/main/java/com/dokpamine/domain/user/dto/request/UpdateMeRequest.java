package com.dokpamine.domain.user.dto.request;

import com.dokpamine.global.util.TextValues;

import jakarta.validation.constraints.*;

public record UpdateMeRequest(
        @Size(min = 1, max = 30, message = "닉네임을 1~30자로 입력해 주세요.") String nickname,
        Boolean rankingOptIn) {
    public UpdateMeRequest {
        nickname = TextValues.trim(nickname);
    }
}
