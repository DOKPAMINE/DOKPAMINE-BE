package com.dokpamine.feed

import com.dokpamine.common.ApiException
import com.dokpamine.config.userId
import com.dokpamine.record.Review
import com.dokpamine.record.ReviewRepository
import com.dokpamine.record.UserBookRepository
import com.dokpamine.record.Visibility
import com.dokpamine.user.UserRepository
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ReviewLikeId(
    @Column(name = "review_id") val reviewId: UUID = UUID(0, 0),
    @Column(name = "user_id") val userId: UUID = UUID(0, 0),
) : Serializable

@Entity
@Table(name = "review_likes")
class ReviewLike(
    @EmbeddedId val id: ReviewLikeId,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)

interface ReviewLikeRepository : JpaRepository<ReviewLike, ReviewLikeId> {
    fun countByIdReviewId(reviewId: UUID): Long
}

data class FeedItem(
    val id: UUID,
    val nickname: String,
    val bookTitle: String,
    val bookAuthor: String,
    val genre: String,
    val oneLine: String?,
    val body: String?,
    val publishedAt: Instant?,
    val likeCount: Long,
    val likedByMe: Boolean,
    val mine: Boolean,
)

data class LikeResponse(val likeCount: Long, val likedByMe: Boolean)

/** 공개 감상 피드. 비공개 글은 작성자 본인 외에는 목록·상세 모두 404 로 응답한다(NFR-09). */
@RestController
@RequestMapping("/api/feed")
class FeedController(
    private val reviews: ReviewRepository,
    private val books: UserBookRepository,
    private val users: UserRepository,
    private val likes: ReviewLikeRepository,
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(auth: Authentication): List<FeedItem> {
        val me = auth.userId()
        return reviews.findAllByVisibilityAndDeletedFalseOrderByPublishedAtDesc(Visibility.PUBLIC)
            .take(100)
            .mapNotNull { toItem(it, me) }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    fun detail(auth: Authentication, @PathVariable id: UUID): FeedItem {
        val me = auth.userId()
        return toItem(visibleReview(id, me), me) ?: throw ApiException.notFound("감상")
    }

    @PostMapping("/{id}/like")
    @Transactional
    fun like(auth: Authentication, @PathVariable id: UUID): LikeResponse {
        val me = auth.userId()
        val review = visibleReview(id, me)
        val likeId = ReviewLikeId(review.id, me)
        if (!likes.existsById(likeId)) likes.save(ReviewLike(likeId))
        return LikeResponse(likes.countByIdReviewId(review.id), true)
    }

    @DeleteMapping("/{id}/like")
    @Transactional
    fun unlike(auth: Authentication, @PathVariable id: UUID): LikeResponse {
        val me = auth.userId()
        val review = visibleReview(id, me)
        likes.deleteById(ReviewLikeId(review.id, me))
        return LikeResponse(likes.countByIdReviewId(review.id), false)
    }

    private fun visibleReview(id: UUID, me: UUID): Review {
        val review = reviews.findByIdOrNull(id)
        if (review == null || review.deleted || (review.visibility != Visibility.PUBLIC && review.userId != me)) {
            throw ApiException.notFound("감상")
        }
        return review
    }

    private fun toItem(review: Review, me: UUID): FeedItem? {
        val book = books.findByIdOrNull(review.bookId)?.takeUnless { it.deleted } ?: return null
        val author = users.findByIdOrNull(review.userId) ?: return null
        return FeedItem(
            id = review.id,
            nickname = author.nickname,
            bookTitle = book.title,
            bookAuthor = book.author,
            genre = book.genre,
            oneLine = review.oneLine,
            body = review.body,
            publishedAt = review.publishedAt,
            likeCount = likes.countByIdReviewId(review.id),
            likedByMe = likes.existsById(ReviewLikeId(review.id, me)),
            mine = review.userId == me,
        )
    }
}
