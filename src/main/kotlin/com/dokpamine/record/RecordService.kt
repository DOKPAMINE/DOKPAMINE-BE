package com.dokpamine.record

import com.dokpamine.common.ApiException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 클라이언트(오프라인 우선)에서 올라오는 개인 기록을 멱등하게 반영한다.
 * - 같은 ID 재전송: 덮어쓰기 (중복 생성 0건)
 * - 다른 사용자의 ID: 403
 * - 아직 서버에 없는 책을 참조: 409 (클라이언트는 책을 먼저 올린 뒤 재시도)
 */
@Service
@Transactional
class RecordService(
    private val books: UserBookRepository,
    private val sessions: ReadingSessionRepository,
    private val reviews: ReviewRepository,
    private val quotes: QuoteRepository,
) {

    fun upsertBook(userId: UUID, dto: BookDto): BookDto {
        val existing = books.findByIdOrNull(dto.id)
        if (existing != null && existing.userId != userId) throw ApiException.forbidden()
        if (dto.currentPage > dto.totalPages) throw ApiException.badRequest("현재 페이지가 전체 쪽수보다 클 수 없어요.")
        val book = existing ?: UserBook(
            id = dto.id, userId = userId, isbn = null, title = "", author = "", publisher = null, totalPages = 1,
            genre = "", coverUrl = null, status = dto.status, currentPage = 0, completedCount = 0, completedAt = null, deleted = false,
            createdAt = dto.createdAt, updatedAt = dto.updatedAt,
        )
        book.apply {
            isbn = dto.isbn
            title = dto.title.trim()
            author = dto.author.trim()
            publisher = dto.publisher
            totalPages = dto.totalPages
            genre = dto.genre
            coverUrl = dto.coverUrl
            status = dto.status
            currentPage = dto.currentPage
            completedCount = dto.completedCount
            completedAt = dto.completedAt
            deleted = dto.deleted
            updatedAt = dto.updatedAt
        }
        return BookDto(books.save(book))
    }

    fun upsertSession(userId: UUID, dto: SessionDto): SessionDto {
        val existing = sessions.findByIdOrNull(dto.id)
        if (existing != null && existing.userId != userId) throw ApiException.forbidden()
        val book = ownedBook(userId, dto.bookId)
        if (dto.endedAt.isBefore(dto.startedAt)) throw ApiException.badRequest("종료 시각이 시작 시각보다 빨라요.")
        if (dto.endPage != null && dto.endPage > book.totalPages) {
            throw ApiException.badRequest("종료 페이지가 전체 쪽수(${book.totalPages})를 넘어요.")
        }
        val session = existing ?: ReadingSession(
            id = dto.id, userId = userId, bookId = dto.bookId, startedAt = dto.startedAt, endedAt = dto.endedAt,
            activeSeconds = dto.activeSeconds, startPage = dto.startPage, endPage = dto.endPage, source = dto.source,
            deleted = dto.deleted, createdAt = dto.createdAt,
        )
        session.apply {
            bookId = dto.bookId
            startedAt = dto.startedAt
            endedAt = dto.endedAt
            activeSeconds = dto.activeSeconds
            startPage = dto.startPage
            endPage = dto.endPage
            source = dto.source
            deleted = dto.deleted
        }
        return SessionDto(sessions.save(session))
    }

    fun upsertReview(userId: UUID, dto: ReviewDto): ReviewDto {
        val existing = reviews.findByIdOrNull(dto.id)
        if (existing != null && existing.userId != userId) throw ApiException.forbidden()
        ownedBook(userId, dto.bookId)
        if (!dto.deleted && dto.oneLine.isNullOrBlank() && dto.body.isNullOrBlank()) {
            throw ApiException.badRequest("한줄평이나 독후감 중 하나는 입력해야 해요.")
        }
        val review = existing ?: Review(
            id = dto.id, userId = userId, bookId = dto.bookId, oneLine = null, body = null,
            visibility = Visibility.PRIVATE, publishedAt = null, deleted = false,
            createdAt = dto.createdAt, updatedAt = dto.updatedAt,
        )
        review.apply {
            bookId = dto.bookId
            oneLine = dto.oneLine?.trim()?.ifEmpty { null }
            body = dto.body?.trim()?.ifEmpty { null }
            if (dto.visibility == Visibility.PUBLIC && visibility != Visibility.PUBLIC) publishedAt = Instant.now()
            if (dto.visibility == Visibility.PRIVATE) publishedAt = null
            visibility = dto.visibility
            deleted = dto.deleted
            updatedAt = dto.updatedAt
        }
        return ReviewDto(reviews.save(review))
    }

    fun upsertQuote(userId: UUID, dto: QuoteDto): QuoteDto {
        val existing = quotes.findByIdOrNull(dto.id)
        if (existing != null && existing.userId != userId) throw ApiException.forbidden()
        ownedBook(userId, dto.bookId)
        val quote = existing ?: Quote(
            id = dto.id, userId = userId, bookId = dto.bookId, text = dto.text, page = dto.page, memo = dto.memo,
            deleted = dto.deleted, createdAt = dto.createdAt, updatedAt = dto.updatedAt,
        )
        quote.apply {
            bookId = dto.bookId
            text = dto.text.trim()
            page = dto.page
            memo = dto.memo
            deleted = dto.deleted
            updatedAt = dto.updatedAt
        }
        return QuoteDto(quotes.save(quote))
    }

    @Transactional(readOnly = true)
    fun snapshot(userId: UUID) = SnapshotResponse(
        books = books.findAllByUserIdAndDeletedFalse(userId).map(::BookDto),
        sessions = sessions.findAllByUserIdAndDeletedFalse(userId).map(::SessionDto),
        reviews = reviews.findAllByUserIdAndDeletedFalse(userId).map(::ReviewDto),
        quotes = quotes.findAllByUserIdAndDeletedFalse(userId).map(::QuoteDto),
    )

    private fun ownedBook(userId: UUID, bookId: UUID): UserBook {
        val book = books.findByIdOrNull(bookId)
            ?: throw ApiException.conflict("BOOK_NOT_SYNCED", "책 정보가 아직 서버에 없어요.")
        if (book.userId != userId) throw ApiException.forbidden()
        return book
    }
}
