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
        @NotNull UUID id,
        @NotNull UUID bookId,
        @Size(max = 100) String oneLine,
        @Size(max = 5000) String body,
        @JsonDeserialize(using = PrivateVisibilityDeserializer.class) Visibility visibility,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @PositiveOrZero Long version) {
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
