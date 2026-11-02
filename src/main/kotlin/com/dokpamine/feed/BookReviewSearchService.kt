package com.dokpamine.feed

import com.dokpamine.catalog.Isbn
import com.dokpamine.common.ApiException
import com.dokpamine.record.Review
import com.dokpamine.record.ReviewRepository
import com.dokpamine.record.UserBook
import com.dokpamine.record.UserBookRepository
import com.dokpamine.record.Visibility
import com.dokpamine.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class BookReviewSearchService(
    private val reviews: ReviewRepository,
    private val books: UserBookRepository,
    private val users: UserRepository,
    private val likes: ReviewLikeRepository,
) {
    @Transactional(readOnly = true)
    fun search(query: String, me: UUID): List<BookReviewGroup> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) throw ApiException.badRequest("검색어를 입력해 주세요.")

        val publicReviews = reviews.findAllByVisibilityAndDeletedFalseOrderByPublishedAtDesc(Visibility.PUBLIC)
            .sortedWith(compareByDescending<Review> { it.publishedAt ?: Instant.MIN }.thenBy { it.id })
        val publicBooks = books.findAllById(publicReviews.map { it.bookId }.distinct())
            .filterNot { it.deleted }
            .associateBy { it.id }

        // FR-16: 먼저 묶어야 같은 ISBN의 제목 표기가 달라도 모든 공개 감상을 함께 반환한다.
        val matches = publicReviews.mapNotNull { review -> publicBooks[review.bookId]?.let { review to it } }
            .groupBy { (_, book) -> bookKey(book) }
            .filterValues { entries -> entries.any { (_, book) -> normalize(book.title).contains(normalizedQuery) } }
        val authors = users.findAllById(matches.values.flatten().map { it.first.userId }.distinct())
            .associateBy { it.id }

        return matches.mapNotNull { (key, entries) ->
            val items = entries.mapNotNull { (review, book) ->
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
                    likeCount = likes.countByIdReviewId(review.id),
                    likedByMe = likes.existsById(ReviewLikeId(review.id, me)),
                    mine = review.userId == me,
                )
            }
            val latest = items.firstOrNull() ?: return@mapNotNull null
            BookReviewGroup(key, latest.bookTitle, latest.bookAuthor, isbn(entries.first().second), items)
        }
    }

    private fun bookKey(book: UserBook): String {
        isbn(book)?.let { return "isbn:$it" }
        val titleAndAuthor = "${normalize(book.title)}\n${normalize(book.author)}"
        return "text:" + Base64.getUrlEncoder().withoutPadding().encodeToString(titleAndAuthor.toByteArray(Charsets.UTF_8))
    }

    private fun isbn(book: UserBook): String? = book.isbn?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { Isbn.normalize(it) ?: it }

    private fun normalize(value: String): String = whitespace.replace(value, " ").trim().lowercase(Locale.ROOT)

    private companion object {
        val whitespace = Regex("[\\s\\p{Z}]+")
    }
}
