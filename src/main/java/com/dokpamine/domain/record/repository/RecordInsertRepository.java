package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.Quote;
import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.UserBook;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class RecordInsertRepository {
    private final JdbcClient jdbc;
    private final EntityManager entityManager;

    public RecordInsertRepository(JdbcClient jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    public boolean insert(UserBook book) {
        return insert("""
                insert into user_books (id, user_id, isbn, title, author, publisher, total_pages,
                    genre, cover_url, status, current_page, completed_count, completed_at, deleted,
                    created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """,
                book.getId(), book.getUserId(), book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getPublisher(), book.getTotalPages(), book.getGenre(), book.getCoverUrl(),
                book.getStatus().name(), book.getCurrentPage(), book.getCompletedCount(),
                timestamp(book.getCompletedAt()), book.getDeleted(), timestamp(book.getCreatedAt()),
                timestamp(book.getUpdatedAt()));
    }

    public boolean insert(ReadingSession session) {
        return insert("""
                insert into reading_sessions (id, user_id, book_id, started_at, ended_at,
                    active_seconds, start_page, end_page, source, deleted, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """,
                session.getId(), session.getUserId(), session.getBookId(),
                timestamp(session.getStartedAt()), timestamp(session.getEndedAt()),
                session.getActiveSeconds(), session.getStartPage(), session.getEndPage(),
                session.getSource().name(), session.getDeleted(), timestamp(session.getCreatedAt()));
    }

    public boolean insert(Quote quote) {
        return insert("""
                insert into quotes (id, user_id, book_id, text, page, memo, deleted, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """,
                quote.getId(), quote.getUserId(), quote.getBookId(), quote.getText(), quote.getPage(),
                quote.getMemo(), quote.getDeleted(), timestamp(quote.getCreatedAt()),
                timestamp(quote.getUpdatedAt()));
    }

    private boolean insert(String sql, Object... params) {
        // 같은 트랜잭션에서 JPA로 추가한 외래 키 참조도 INSERT 전에 반영한다.
        entityManager.flush();
        return jdbc.sql(sql).params(params).update() == 1;
    }

    private static OffsetDateTime timestamp(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
