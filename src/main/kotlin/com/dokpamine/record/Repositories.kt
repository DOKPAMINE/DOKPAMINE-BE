package com.dokpamine.record

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface UserBookRepository : JpaRepository<UserBook, UUID> {
    fun findAllByUserIdAndDeletedFalse(userId: UUID): List<UserBook>
}

interface ReadingSessionRepository : JpaRepository<ReadingSession, UUID> {
    fun findAllByUserIdAndDeletedFalse(userId: UUID): List<ReadingSession>

    fun findAllByUserIdInAndSourceAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
        userIds: Collection<UUID>,
        source: SessionSource,
        from: Instant,
        to: Instant,
    ): List<ReadingSession>
}

interface ReviewRepository : JpaRepository<Review, UUID> {
    fun findAllByUserIdAndDeletedFalse(userId: UUID): List<Review>
    fun findAllByVisibilityAndDeletedFalseOrderByPublishedAtDesc(visibility: Visibility): List<Review>
}

interface QuoteRepository : JpaRepository<Quote, UUID> {
    fun findAllByUserIdAndDeletedFalse(userId: UUID): List<Quote>
}
