package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.Visibility;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.*;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findAllByUserIdAndDeletedFalse(UUID userId);

    @Query(
            """
                select r from Review r join UserBook b on b.id = r.bookId
                where r.id = :id and r.deleted = false and b.deleted = false
            """)
    Optional<Review> findActiveReview(UUID id);

    @Query(
            """
                select r from Review r join UserBook b on b.id = r.bookId
                where r.visibility = :visibility and r.deleted = false and b.deleted = false
                order by r.publishedAt desc
            """)
    List<Review> findFeedReviews(Visibility visibility, Pageable pageable);
}
