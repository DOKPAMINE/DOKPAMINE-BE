package com.dokpamine.domain.record.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading_sessions")
public class ReadingSession {
    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "active_seconds")
    private int activeSeconds;

    @Column(name = "start_page")
    private int startPage;

    @Column(name = "end_page")
    private Integer endPage;

    @Enumerated(EnumType.STRING)
    private SessionSource source;

    private boolean deleted;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ReadingSession() {}

    public ReadingSession(
            UUID id,
            UUID userId,
            UUID bookId,
            Instant startedAt,
            Instant endedAt,
            int activeSeconds,
            int startPage,
            Integer endPage,
            SessionSource source,
            boolean deleted,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.bookId = bookId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.activeSeconds = activeSeconds;
        this.startPage = startPage;
        this.endPage = endPage;
        this.source = source;
        this.deleted = deleted;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getBookId() {
        return bookId;
    }

    public void setBookId(UUID bookId) {
        this.bookId = bookId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public int getActiveSeconds() {
        return activeSeconds;
    }

    public void setActiveSeconds(int activeSeconds) {
        this.activeSeconds = activeSeconds;
    }

    public int getStartPage() {
        return startPage;
    }

    public void setStartPage(int startPage) {
        this.startPage = startPage;
    }

    public Integer getEndPage() {
        return endPage;
    }

    public void setEndPage(Integer endPage) {
        this.endPage = endPage;
    }

    public SessionSource getSource() {
        return source;
    }

    public void setSource(SessionSource source) {
        this.source = source;
    }

    public boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
