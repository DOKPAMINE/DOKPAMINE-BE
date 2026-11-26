package com.dokpamine.domain.feed.repository;

import com.dokpamine.domain.feed.entity.ReviewLike;
import com.dokpamine.domain.feed.entity.ReviewLikeId;
import com.dokpamine.domain.record.entity.Visibility;

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

    /** FR-09 NFR-09 내가 좋아요한 감상 중 지금도 공개이고 삭제되지 않은 감상만 고른다. */
    @Query(
            """
                select r.id as reviewId, l.createdAt as likedAt, b.id as userBookId,
                       r.userId as authorId, b.isbn as isbn, b.title as title,
                       b.author as author, b.coverUrl as coverUrl
                from ReviewLike l
                join Review r on r.id = l.id.reviewId
                join UserBook b on b.id = r.bookId
                where l.id.userId = :userId and r.visibility = :visibility
                and r.deleted = false and b.deleted = false
            """)
    List<LikedReviewRow> findVisibleLikedReviews(UUID userId, Visibility visibility);
}
