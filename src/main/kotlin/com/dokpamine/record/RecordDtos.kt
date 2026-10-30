package com.dokpamine.record

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class BookDto(
    val id: UUID,
    @field:Pattern(regexp = "^(\\d{10}|\\d{13})$", message = "ISBN 은 10자리 또는 13자리 숫자여야 해요.")
    val isbn: String? = null,
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:NotBlank @field:Size(max = 100) val author: String,
    @field:Size(max = 100) val publisher: String? = null,
    @field:Min(1) val totalPages: Int,
    @field:NotBlank @field:Size(max = 30) val genre: String,
    @field:Size(max = 500) val coverUrl: String? = null,
    val status: BookStatus,
    @field:Min(0) val currentPage: Int,
    @field:Min(0) val completedCount: Int,
    val completedAt: Instant? = null,
    val deleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    constructor(b: UserBook) : this(
        b.id, b.isbn, b.title, b.author, b.publisher, b.totalPages, b.genre, b.coverUrl,
        b.status, b.currentPage, b.completedCount, b.completedAt, b.deleted, b.createdAt, b.updatedAt,
    )
}

data class SessionDto(
    val id: UUID,
    val bookId: UUID,
    val startedAt: Instant,
    val endedAt: Instant,
    @field:Min(0) val activeSeconds: Int,
    @field:Min(0) val startPage: Int,
    @field:Min(0) val endPage: Int? = null,
    val source: SessionSource,
    val deleted: Boolean = false,
    val createdAt: Instant,
) {
    constructor(s: ReadingSession) : this(
        s.id, s.bookId, s.startedAt, s.endedAt, s.activeSeconds, s.startPage, s.endPage, s.source, s.deleted, s.createdAt,
    )
}

data class ReviewDto(
    val id: UUID,
    val bookId: UUID,
    @field:Size(max = 100) val oneLine: String? = null,
    @field:Size(max = 5000) val body: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
    val deleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    constructor(r: Review) : this(r.id, r.bookId, r.oneLine, r.body, r.visibility, r.deleted, r.createdAt, r.updatedAt)
}

data class QuoteDto(
    val id: UUID,
    val bookId: UUID,
    @field:NotBlank @field:Size(max = 1000) val text: String,
    @field:Min(0) val page: Int? = null,
    @field:Size(max = 500) val memo: String? = null,
    val deleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    constructor(q: Quote) : this(q.id, q.bookId, q.text, q.page, q.memo, q.deleted, q.createdAt, q.updatedAt)
}

data class SnapshotResponse(
    val books: List<BookDto>,
    val sessions: List<SessionDto>,
    val reviews: List<ReviewDto>,
    val quotes: List<QuoteDto>,
)
