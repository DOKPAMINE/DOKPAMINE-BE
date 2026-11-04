package com.dokpamine.domain.feed.repository;

import com.dokpamine.domain.feed.entity.ReviewLike;
import com.dokpamine.domain.feed.entity.ReviewLikeId;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.*;

public interface FeedLikeReadRepository extends Repository<ReviewLike, ReviewLikeId> {
    @Query(
            """
                select l.id.reviewId as reviewId, count(l) as likeCount from ReviewLike l
                where l.id.reviewId in :reviewIds group by l.id.reviewId
            """)
    List<ReviewLikeCount> countByReviewIds(Collection<UUID> reviewIds);

    @Query(
            "select l.id.reviewId from ReviewLike l where l.id.userId = :userId and l.id.reviewId"
                    + " in :reviewIds")
    List<UUID> findLikedReviewIds(UUID userId, Collection<UUID> reviewIds);
}
