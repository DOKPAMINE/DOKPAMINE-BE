package com.dokpamine.domain.record.dto;

import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.SessionSource;
import com.dokpamine.global.apiPayload.deserializer.DefaultFalseBooleanDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record SessionDto(
        @NotNull UUID id,
        @NotNull UUID bookId,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @JsonProperty(required = true) @Min(0) int activeSeconds,
        @JsonProperty(required = true) @Min(0) int startPage,
        @Min(0) Integer endPage,
        @NotNull SessionSource source,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull Instant createdAt) {
    public SessionDto {
        if (deleted == null) deleted = false;
    }

    public SessionDto(ReadingSession value) {
        this(
                value.getId(),
                value.getBookId(),
                value.getStartedAt(),
                value.getEndedAt(),
                value.getActiveSeconds(),
                value.getStartPage(),
                value.getEndPage(),
                value.getSource(),
                value.getDeleted(),
                value.getCreatedAt());
    }
}
