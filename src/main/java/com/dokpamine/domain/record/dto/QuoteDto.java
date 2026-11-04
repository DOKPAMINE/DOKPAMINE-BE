package com.dokpamine.domain.record.dto;

import com.dokpamine.domain.record.entity.Quote;
import com.dokpamine.global.apiPayload.deserializer.DefaultFalseBooleanDeserializer;

import jakarta.validation.constraints.*;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record QuoteDto(
        @NotNull UUID id,
        @NotNull UUID bookId,
        @NotNull @NotBlank @Size(max = 1000) String text,
        @Min(0) Integer page,
        @Size(max = 500) String memo,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt) {
    public QuoteDto {
        if (deleted == null) deleted = false;
    }

    public QuoteDto(Quote value) {
        this(
                value.getId(),
                value.getBookId(),
                value.getText(),
                value.getPage(),
                value.getMemo(),
                value.getDeleted(),
                value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
