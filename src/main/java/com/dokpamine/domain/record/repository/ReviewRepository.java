package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.Visibility;

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

    /** FR-08 사람의 책마다 가장 최근에 새로 쓴 공개 독후감 한 건을 박스 순서대로 고른다. */
    @Query(
            value =
                    """
                        select cast(r.id as varchar) from (
                            select id, book_id, body_written_at,
                                   row_number() over (
                                       partition by book_id
                                       order by body_written_at desc, cast(id as varchar) asc) as position
                            from reviews
                            where visibility = 'PUBLIC' and deleted = false
                              and body is not null and body_written_at is not null
                        ) r join user_books b on b.id = r.book_id
                        where r.position = 1 and b.deleted = false
                        order by r.body_written_at desc, cast(r.id as varchar) asc
                        limit :limit
                    """,
            nativeQuery = true)
    List<String> findFeedBoxReviewIds(int limit);

    @Query(
            """
                select r from Review r join UserBook b on b.id = r.bookId
                where r.bookId = :bookId and r.visibility = :visibility
                and r.deleted = false and b.deleted = false
                order by r.publishedAt desc nulls last, cast(r.id as String) asc
            """)
    List<Review> findPublicBookReviews(UUID bookId, Visibility visibility);
}
