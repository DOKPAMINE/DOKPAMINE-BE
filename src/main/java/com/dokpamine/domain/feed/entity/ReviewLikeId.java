package com.dokpamine.domain.feed.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.*;

@Embeddable
public class ReviewLikeId implements Serializable {
    @Column(name = "review_id")
    private UUID reviewId;

    @Column(name = "user_id")
    private UUID userId;

    protected ReviewLikeId() {}

    public ReviewLikeId(UUID reviewId, UUID userId) {
        this.reviewId = reviewId;
        this.userId = userId;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof ReviewLikeId other
                && Objects.equals(reviewId, other.reviewId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reviewId, userId);
    }
}
