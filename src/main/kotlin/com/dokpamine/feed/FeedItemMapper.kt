package com.dokpamine.feed

import com.dokpamine.record.Review
import com.dokpamine.record.UserBookRepository
import com.dokpamine.user.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class FeedItemMapper(
    private val books: UserBookRepository,
    private val users: UserRepository,
    private val likes: FeedLikeReadRepository,
) {
    fun toItems(reviews: List<Review>, me: UUID): List<FeedItem> {
        if (reviews.isEmpty()) return emptyList()
        val publicBooks = books.findAllById(reviews.map { it.bookId }.distinct())
            .filterNot { it.deleted }.associateBy { it.id }
        val authors = users.findAllById(reviews.map { it.userId }.distinct()).associateBy { it.id }
        val reviewIds = reviews.map { it.id }
        val likeCounts = likes.countByReviewIds(reviewIds).associate { it.reviewId to it.likeCount }
        val likedIds = likes.findLikedReviewIds(me, reviewIds).toSet()

        // NFR-09: 호출자가 권한을 확인한 감상에서 기존 FeedItem 공개 필드만 조립한다.
        return reviews.mapNotNull { review ->
            val book = publicBooks[review.bookId] ?: return@mapNotNull null
            val author = authors[review.userId] ?: return@mapNotNull null
            FeedItem(
                id = review.id,
                nickname = author.nickname,
                bookTitle = book.title,
                bookAuthor = book.author,
                genre = book.genre,
                oneLine = review.oneLine,
                body = review.body,
                publishedAt = review.publishedAt,
                likeCount = likeCounts[review.id] ?: 0,
                likedByMe = review.id in likedIds,
                mine = review.userId == me,
            )
        }
    }
}
