package com.dokpamine.domain.record.service;

import com.dokpamine.domain.record.dto.BookDto;
import com.dokpamine.domain.record.dto.QuoteDto;
import com.dokpamine.domain.record.dto.ReviewDto;
import com.dokpamine.domain.record.dto.SessionDto;
import com.dokpamine.domain.record.dto.response.SnapshotResponse;
import com.dokpamine.domain.record.entity.Quote;
import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.SessionSource;
import com.dokpamine.domain.record.entity.UserBook;
import com.dokpamine.domain.record.entity.Visibility;
import com.dokpamine.domain.record.repository.QuoteRepository;
import com.dokpamine.domain.record.repository.ReadingSessionRepository;
import com.dokpamine.domain.record.repository.ReviewRepository;
import com.dokpamine.domain.record.repository.UserBookRepository;
import com.dokpamine.global.apiPayload.exception.ApiException;
import com.dokpamine.global.util.TextValues;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class RecordService {
    private final UserBookRepository books;
    private final ReadingSessionRepository sessions;
    private final ReviewRepository reviews;
    private final QuoteRepository quotes;

    public RecordService(
            UserBookRepository books,
            ReadingSessionRepository sessions,
            ReviewRepository reviews,
            QuoteRepository quotes) {
        this.books = books;
        this.sessions = sessions;
        this.reviews = reviews;
        this.quotes = quotes;
    }

    public BookDto upsertBook(UUID userId, BookDto dto) {
        UserBook book = books.findById(dto.id()).orElse(null);
        if (book != null) requireOwner(book.getUserId(), userId);
        if (dto.currentPage() > dto.totalPages())
            throw ApiException.badRequest("현재 페이지가 전체 쪽수보다 클 수 없어요.");
        if (book == null)
            book =
                    new UserBook(
                            dto.id(),
                            userId,
                            null,
                            "",
                            "",
                            null,
                            1,
                            "",
                            null,
                            dto.status(),
                            0,
                            0,
                            null,
                            false,
                            dto.createdAt(),
                            dto.updatedAt());
        book.setIsbn(dto.isbn());
        book.setTitle(TextValues.trim(dto.title()));
        book.setAuthor(TextValues.trim(dto.author()));
        book.setPublisher(dto.publisher());
        book.setTotalPages(dto.totalPages());
        book.setGenre(dto.genre());
        book.setCoverUrl(dto.coverUrl());
        book.setStatus(dto.status());
        book.setCurrentPage(dto.currentPage());
        book.setCompletedCount(dto.completedCount());
        book.setCompletedAt(dto.completedAt());
        book.setDeleted(dto.deleted());
        book.setUpdatedAt(dto.updatedAt());
        return new BookDto(books.save(book));
    }

    public SessionDto upsertSession(UUID userId, SessionDto dto) {
        ReadingSession session = sessions.findById(dto.id()).orElse(null);
        if (session != null) requireOwner(session.getUserId(), userId);
        UserBook book = ownedBook(userId, dto.bookId());
        if (dto.endedAt().isBefore(dto.startedAt()))
            throw ApiException.badRequest("종료 시각이 시작 시각보다 빨라요.");
        if (dto.endPage() != null && dto.endPage() > book.getTotalPages()) {
            throw ApiException.badRequest("종료 페이지가 전체 쪽수(" + book.getTotalPages() + ")를 넘어요.");
        }
        SessionSource source = dto.source();
        if (session != null
                && (session.getSource() == SessionSource.MANUAL
                        || dto.source() == SessionSource.MANUAL
                        || !atDatabasePrecision(session.getStartedAt())
                                .equals(atDatabasePrecision(dto.startedAt()))
                        || !atDatabasePrecision(session.getEndedAt())
                                .equals(atDatabasePrecision(dto.endedAt()))
                        || session.getActiveSeconds() != dto.activeSeconds()))
            source = SessionSource.MANUAL;
        if (session == null)
            session =
                    new ReadingSession(
                            dto.id(),
                            userId,
                            dto.bookId(),
                            dto.startedAt(),
                            dto.endedAt(),
                            dto.activeSeconds(),
                            dto.startPage(),
                            dto.endPage(),
                            source,
                            dto.deleted(),
                            dto.createdAt());
        session.setBookId(dto.bookId());
        session.setStartedAt(dto.startedAt());
        session.setEndedAt(dto.endedAt());
        session.setActiveSeconds(dto.activeSeconds());
        session.setStartPage(dto.startPage());
        session.setEndPage(dto.endPage());
        session.setSource(source);
        session.setDeleted(dto.deleted());
        return new SessionDto(sessions.save(session));
    }

    public ReviewDto upsertReview(UUID userId, ReviewDto dto) {
        Review review = reviews.findById(dto.id()).orElse(null);
        if (review != null) requireOwner(review.getUserId(), userId);
        ownedBook(userId, dto.bookId());
        if (!dto.deleted() && TextValues.isBlank(dto.oneLine()) && TextValues.isBlank(dto.body()))
            throw ApiException.badRequest("한줄평이나 독후감 중 하나는 입력해야 해요.");
        String oneLine = TextValues.trimToNull(dto.oneLine());
        String body = TextValues.trimToNull(dto.body());
        boolean creating = review == null;
        if (creating) {
            if (dto.version() != null) throw reviewVersionConflict();
            review =
                    new Review(
                            dto.id(),
                            userId,
                            dto.bookId(),
                            null,
                            null,
                            Visibility.PRIVATE,
                            null,
                            false,
                            dto.createdAt(),
                            dto.updatedAt());
        } else {
            // 응답을 잃은 PUT은 저장된 상태와 같으면 버전을 올리지 않는다(NFR-05).
            if (review.getBookId().equals(dto.bookId())
                    && Objects.equals(review.getOneLine(), oneLine)
                    && Objects.equals(review.getBody(), body)
                    && review.getVisibility() == dto.visibility()
                    && review.getDeleted() == dto.deleted()
                    && atDatabasePrecision(review.getUpdatedAt())
                            .equals(atDatabasePrecision(dto.updatedAt()))) {
                return new ReviewDto(review);
            }
            if (!review.getVersion().equals(dto.version())) throw reviewVersionConflict();
        }
        review.setBookId(dto.bookId());
        review.setOneLine(oneLine);
        review.setBody(body);
        if (dto.visibility() == Visibility.PUBLIC && review.getVisibility() != Visibility.PUBLIC)
            review.setPublishedAt(Instant.now());
        if (dto.visibility() == Visibility.PRIVATE) review.setPublishedAt(null);
        review.setVisibility(dto.visibility());
        review.setDeleted(dto.deleted());
        review.setUpdatedAt(dto.updatedAt());
        try {
            // 버전 비교 이후의 동시 변경도 DB에서 검증하고 저장된 버전을 응답에 담는다.
            return new ReviewDto(reviews.saveAndFlush(review));
        } catch (OptimisticLockingFailureException e) {
            throw reviewVersionConflict();
        } catch (DataIntegrityViolationException e) {
            // 같은 UUID로 동시에 생성하면 먼저 커밋된 감상 한 건만 남긴다.
            if (creating && e.getMostSpecificCause() instanceof SQLException sql
                    && "23505".equals(sql.getSQLState())) throw reviewVersionConflict();
            throw e;
        }
    }

    private static ApiException reviewVersionConflict() {
        return ApiException.conflict("REVIEW_VERSION_CONFLICT", "최신 감상을 확인한 뒤 다시 수정해 주세요.");
    }

    public QuoteDto upsertQuote(UUID userId, QuoteDto dto) {
        Quote quote = quotes.findById(dto.id()).orElse(null);
        if (quote != null) requireOwner(quote.getUserId(), userId);
        ownedBook(userId, dto.bookId());
        if (quote == null)
            quote =
                    new Quote(
                            dto.id(),
                            userId,
                            dto.bookId(),
                            dto.text(),
                            dto.page(),
                            dto.memo(),
                            dto.deleted(),
                            dto.createdAt(),
                            dto.updatedAt());
        quote.setBookId(dto.bookId());
        quote.setText(TextValues.trim(dto.text()));
        quote.setPage(dto.page());
        quote.setMemo(dto.memo());
        quote.setDeleted(dto.deleted());
        quote.setUpdatedAt(dto.updatedAt());
        return new QuoteDto(quotes.save(quote));
    }

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot(UUID userId) {
        return new SnapshotResponse(
                books.findAllByUserIdAndDeletedFalse(userId).stream().map(BookDto::new).toList(),
                sessions.findAllByUserIdAndDeletedFalse(userId).stream()
                        .map(SessionDto::new)
                        .toList(),
                reviews.findAllByUserIdAndDeletedFalse(userId).stream()
                        .map(ReviewDto::new)
                        .toList(),
                quotes.findAllByUserIdAndDeletedFalse(userId).stream().map(QuoteDto::new).toList());
    }

    private UserBook ownedBook(UUID userId, UUID bookId) {
        UserBook book =
                books.findById(bookId)
                        .orElseThrow(() ->
                                ApiException.conflict("BOOK_NOT_SYNCED", "책 정보가 아직 서버에 없어요."));
        requireOwner(book.getUserId(), userId);
        return book;
    }

    private static void requireOwner(UUID owner, UUID userId) {
        if (!owner.equals(userId)) throw ApiException.forbidden();
    }

    // NFR-05: timestamp(6)의 반올림과 같은 정밀도로 비교하여 멱등 재전송을 보존한다.
    private static Instant atDatabasePrecision(Instant value) {
        return value.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    }
}
