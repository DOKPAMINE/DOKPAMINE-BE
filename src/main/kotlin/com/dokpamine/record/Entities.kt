package com.dokpamine.record

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 모든 개인 기록은 클라이언트가 만든 UUID 를 그대로 PK 로 쓴다.
// 같은 ID 로 여러 번 전송해도 한 건만 남는다(NFR-05).

enum class BookStatus { WANT, READING, DONE }
enum class SessionSource { TIMER, MANUAL }
enum class Visibility { PRIVATE, PUBLIC }

@Entity
@Table(name = "user_books")
class UserBook(
    @Id val id: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    var isbn: String?,
    var title: String,
    var author: String,
    var publisher: String?,
    @Column(name = "total_pages") var totalPages: Int,
    var genre: String,
    @Column(name = "cover_url") var coverUrl: String?,
    @Enumerated(EnumType.STRING) var status: BookStatus,
    @Column(name = "current_page") var currentPage: Int,
    @Column(name = "completed_count") var completedCount: Int,
    @Column(name = "completed_at") var completedAt: Instant?,
    var deleted: Boolean,
    @Column(name = "created_at") val createdAt: Instant,
    @Column(name = "updated_at") var updatedAt: Instant,
)

@Entity
@Table(name = "reading_sessions")
class ReadingSession(
    @Id val id: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(name = "book_id", nullable = false) var bookId: UUID,
    @Column(name = "started_at") var startedAt: Instant,
    @Column(name = "ended_at") var endedAt: Instant,
    @Column(name = "active_seconds") var activeSeconds: Int,
    @Column(name = "start_page") var startPage: Int,
    @Column(name = "end_page") var endPage: Int?,
    @Enumerated(EnumType.STRING) var source: SessionSource,
    var deleted: Boolean,
    @Column(name = "created_at") val createdAt: Instant,
)

@Entity
@Table(name = "reviews")
class Review(
    @Id val id: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(name = "book_id", nullable = false) var bookId: UUID,
    @Column(name = "one_line") var oneLine: String?,
    var body: String?,
    @Enumerated(EnumType.STRING) var visibility: Visibility,
    @Column(name = "published_at") var publishedAt: Instant?,
    var deleted: Boolean,
    @Column(name = "created_at") val createdAt: Instant,
    @Column(name = "updated_at") var updatedAt: Instant,
)

@Entity
@Table(name = "quotes")
class Quote(
    @Id val id: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(name = "book_id", nullable = false) var bookId: UUID,
    var text: String,
    var page: Int?,
    var memo: String?,
    var deleted: Boolean,
    @Column(name = "created_at") val createdAt: Instant,
    @Column(name = "updated_at") var updatedAt: Instant,
)
