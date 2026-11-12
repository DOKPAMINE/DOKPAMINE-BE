package com.dokpamine.domain.record.dto;

import com.dokpamine.domain.record.entity.BookStatus;
import com.dokpamine.domain.record.entity.UserBook;
import com.dokpamine.global.apiPayload.deserializer.DefaultFalseBooleanDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.UUID;

public record BookDto(
        @NotNull(message = "필수 값을 입력해 주세요.") UUID id,
        @Pattern(regexp = "^(\\d{10}|\\d{13})$", message = "ISBN 은 10자리 또는 13자리 숫자여야 해요.")
                String isbn,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @NotBlank(message = "공백이 아닌 값을 입력해 주세요.")
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 200) String title,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @NotBlank(message = "공백이 아닌 값을 입력해 주세요.")
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 100) String author,
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 100) String publisher,
        @JsonProperty(required = true)
        @Min(value = 1, message = "1 이상의 값을 입력해 주세요.") int totalPages,
        @NotNull(message = "필수 값을 입력해 주세요.")
        @NotBlank(message = "공백이 아닌 값을 입력해 주세요.")
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 30) String genre,
        @Size(message = "{max}자 이하로 입력해 주세요.", max = 500) String coverUrl,
        @NotNull(message = "필수 값을 입력해 주세요.") BookStatus status,
        @JsonProperty(required = true)
        @Min(value = 0, message = "0 이상의 값을 입력해 주세요.") int currentPage,
        @JsonProperty(required = true)
        @Min(value = 0, message = "0 이상의 값을 입력해 주세요.") int completedCount,
        Instant completedAt,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant createdAt,
        @NotNull(message = "필수 값을 입력해 주세요.") Instant updatedAt) {
    public BookDto {
        // NFR-05: 클라이언트가 삭제 필드를 생략하면 기존 동기화 계약의 false를 적용한다.
        if (deleted == null) deleted = false;
    }

    public BookDto(UserBook value) {
        this(
                value.getId(),
                value.getIsbn(),
                value.getTitle(),
                value.getAuthor(),
                value.getPublisher(),
                value.getTotalPages(),
                value.getGenre(),
                value.getCoverUrl(),
                value.getStatus(),
                value.getCurrentPage(),
                value.getCompletedCount(),
                value.getCompletedAt(),
                value.getDeleted(),
                value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
