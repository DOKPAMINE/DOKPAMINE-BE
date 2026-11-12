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
        @NotNull(message = "필수 값을 입력해 주세요.") UUID id,
        @NotNull(message = "필수 값을 입력해 주세요.") UUID bookId,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant startedAt,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant endedAt,
        @JsonProperty(required = true)
        @Min(value = 0, message = "0 이상의 값을 입력해 주세요.") int activeSeconds,
        @JsonProperty(required = true)
        @Min(value = 0, message = "0 이상의 값을 입력해 주세요.") int startPage,
        @Min(value = 0, message = "0 이상의 값을 입력해 주세요.") Integer endPage,
        @NotNull(message = "필수 값을 입력해 주세요.") SessionSource source,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant createdAt) {
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
