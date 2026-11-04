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
        @NotNull UUID id,
        @Pattern(regexp = "^(\\d{10}|\\d{13})$", message = "ISBN 은 10자리 또는 13자리 숫자여야 해요.")
                String isbn,
        @NotNull @NotBlank @Size(max = 200) String title,
        @NotNull @NotBlank @Size(max = 100) String author,
        @Size(max = 100) String publisher,
        @JsonProperty(required = true) @Min(1) int totalPages,
        @NotNull @NotBlank @Size(max = 30) String genre,
        @Size(max = 500) String coverUrl,
        @NotNull BookStatus status,
        @JsonProperty(required = true) @Min(0) int currentPage,
        @JsonProperty(required = true) @Min(0) int completedCount,
        Instant completedAt,
        @JsonDeserialize(using = DefaultFalseBooleanDeserializer.class) Boolean deleted,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt) {
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
