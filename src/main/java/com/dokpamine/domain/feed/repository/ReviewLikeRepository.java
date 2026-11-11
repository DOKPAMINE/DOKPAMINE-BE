package com.dokpamine.domain.feed.repository;

import com.dokpamine.domain.feed.entity.ReviewLike;
import com.dokpamine.domain.feed.entity.ReviewLikeId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, ReviewLikeId> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into review_likes (review_id, user_id, created_at)
            values (:reviewId, :userId, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID reviewId, UUID userId);

    long countByIdReviewId(UUID reviewId);
}
