package com.dokpamine.domain.feed.repository;

import com.dokpamine.domain.feed.entity.ReviewLike;
import com.dokpamine.domain.feed.entity.ReviewLikeId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, ReviewLikeId> {
    long countByIdReviewId(UUID reviewId);
}
