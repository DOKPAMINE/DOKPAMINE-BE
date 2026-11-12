package com.dokpamine.domain.record.dto;

import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.Visibility;
import com.dokpamine.domain.record.support.PrivateVisibilityDeserializer;
import com.dokpamine.global.apiPayload.deserializer.DefaultFalseBooleanDeserializer;

import jakarta.validation.constraints.*;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(
        @NotNull(message = "필수 값을 입력해 주세요.") UUID id,
        @NotNull(message = "필수 값을 입력해 주세요.") UUID bookId,
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 100) String oneLine,
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 5000) String body,
        @JsonDeserialize(using = PrivateVisibilityDeserializer.class) Visibility visibility,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant createdAt,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant updatedAt,
        @PositiveOrZero(message = "0 이상의 값을 입력해 주세요.") Long version) {
    public ReviewDto {
        if (visibility == null) visibility = Visibility.PRIVATE;
        if (deleted == null) deleted = false;
    }

    public ReviewDto(Review value) {
        this(
                value.getId(),
                value.getBookId(),
                value.getOneLine(),
                value.getBody(),
                value.getVisibility(),
                value.getDeleted(),
                value.getCreatedAt(),
                value.getUpdatedAt(),
                value.getVersion());
    }
}
