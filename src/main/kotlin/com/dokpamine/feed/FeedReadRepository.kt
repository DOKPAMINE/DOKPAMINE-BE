package com.dokpamine.feed

import com.dokpamine.record.Review
import com.dokpamine.record.UserBook
import com.dokpamine.record.Visibility
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

interface PublicBookSummary {
    val id: String
    val title: String
    val author: String
    val isbn: String?
    val latestPublishedAt: Any?
    val latestReviewId: String
}

// FR-16: 네이티브 timestamptz 결과 타입은 DB와 Hibernate Dialect에 따라 달라진다.
internal fun nativePublishedAtToInstant(value: Any?): Instant? = when (value) {
    null -> null
    is Instant -> value
    is OffsetDateTime -> value.toInstant()
    is Timestamp -> value.toInstant()
    else -> error("지원하지 않는 공개 시각 타입: ${value.javaClass.name}")
}

interface BookTitleCandidate {
    val id: UUID
    val title: String
    val author: String
    val isbn: String?
}

interface BookReviewSearchRepository : Repository<UserBook, UUID> {
    @Query("""
        select b.id as id, b.title as title, b.author as author, b.isbn as isbn
        from UserBook b join Review r on r.bookId = b.id
        where b.deleted = false and lower(b.title) like :pattern escape '!'
        and r.deleted = false and r.visibility = :visibility
        group by b.id, b.title, b.author, b.isbn
        order by max(r.publishedAt) desc nulls last, cast(b.id as String) asc
    """)
    fun findTitleCandidates(pattern: String, visibility: Visibility, pageable: Pageable): List<BookTitleCandidate>

    // FR-16: 제목이 다른 ISBN 사본도 찾되 감상 본문은 읽지 않고 책별 최신 감상만 비교한다.
    @Query(value = """
        select cast(b.id as varchar) as id, b.title, b.author, b.isbn,
               r.published_at as latestPublishedAt, cast(r.id as varchar) as latestReviewId
        from user_books b join (
            select book_id, id, published_at,
                   row_number() over (partition by book_id order by published_at desc nulls last, cast(id as varchar) asc) as position
            from reviews where visibility = 'PUBLIC' and deleted = false
        ) r on r.book_id = b.id and r.position = 1
        where b.deleted = false and (
            lower(b.title) like :pattern escape '!'
            or trim(b.isbn) in (:isbns)
            or upper(replace(replace(trim(b.isbn), '-', ''), ' ', '')) in (:isbnForms)
        )
    """, nativeQuery = true)
    fun findPublicBookSummaries(pattern: String, isbns: Collection<String>, isbnForms: Collection<String>): List<PublicBookSummary>

    @Query("""
        select r from Review r where r.bookId in :bookIds and r.visibility = :visibility and r.deleted = false
        order by r.publishedAt desc nulls last, cast(r.id as String) asc
    """)
    fun findPublicReviews(bookIds: Collection<UUID>, visibility: Visibility, pageable: Pageable): List<Review>
}

interface ReviewLikeCount {
    val reviewId: UUID
    val likeCount: Long
}

interface FeedLikeReadRepository : Repository<ReviewLike, ReviewLikeId> {
    @Query("""
        select l.id.reviewId as reviewId, count(l) as likeCount from ReviewLike l
        where l.id.reviewId in :reviewIds group by l.id.reviewId
    """)
    fun countByReviewIds(reviewIds: Collection<UUID>): List<ReviewLikeCount>

    @Query("select l.id.reviewId from ReviewLike l where l.id.userId = :userId and l.id.reviewId in :reviewIds")
    fun findLikedReviewIds(userId: UUID, reviewIds: Collection<UUID>): List<UUID>
}
