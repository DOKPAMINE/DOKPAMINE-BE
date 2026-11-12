package com.dokpamine.domain.record.dto;

import com.dokpamine.domain.record.entity.Quote;
import com.dokpamine.global.apiPayload.deserializer.DefaultFalseBooleanDeserializer;

import jakarta.validation.constraints.*;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record QuoteDto(
        @NotNull(message = "필수 값을 입력해 주세요.") UUID id,
        @NotNull(message = "필수 값을 입력해 주세요.") UUID bookId,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @NotBlank(message = "공백이 아닌 값을 입력해 주세요.")
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 1000) String text,
        @Min(value = 0, message = "0 이상의 값을 입력해 주세요.") Integer page,
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 500) String memo,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant createdAt,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant updatedAt) {
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
