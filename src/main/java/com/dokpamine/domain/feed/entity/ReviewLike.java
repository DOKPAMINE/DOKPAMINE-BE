package com.dokpamine.domain.feed.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "review_likes")
public class ReviewLike {
    @EmbeddedId private ReviewLikeId id;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ReviewLike() {}

    public ReviewLike(ReviewLikeId id) {
        this(id, Instant.now());
    }

    public ReviewLike(ReviewLikeId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public ReviewLikeId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
