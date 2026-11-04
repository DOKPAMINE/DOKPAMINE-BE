package com.dokpamine.domain.feed.repository;

import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.UserBook;
import com.dokpamine.domain.record.entity.Visibility;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.*;

public interface BookReviewSearchRepository extends Repository<UserBook, UUID> {
    @Query(
            """
                select b.id as id, b.title as title, b.author as author, b.isbn as isbn
                from UserBook b join Review r on r.bookId = b.id
                where b.deleted = false and lower(b.title) like :pattern escape '!'
                and r.deleted = false and r.visibility = :visibility
                group by b.id, b.title, b.author, b.isbn
                order by max(r.publishedAt) desc nulls last, cast(b.id as String) asc
            """)
    List<BookTitleCandidate> findTitleCandidates(
            String pattern, Visibility visibility, Pageable pageable);

    @Query(
            value =
                    """
                        select cast(b.id as varchar) as id, b.title, b.author, b.isbn,
                               r.published_at as latestPublishedAt, cast(r.id as varchar) as latestReviewId
                        from user_books b join (
                            select book_id, id, published_at,
                                   row_number() over (partition by book_id order by published_at desc nulls last, cast(id as varchar) asc) as position
                            from reviews where visibility = 'PUBLIC' and deleted = false
                        ) r on r.book_id = b.id and r.position = 1
                        where b.deleted = false and (
                            lower(b.title) like :pattern escape '!'
                            or trim(b.isbn) in (:isbns)
                            or upper(replace(replace(trim(b.isbn), '-', ''), ' ', '')) in (:isbnForms)
                        )
                    """,
            nativeQuery = true)
    List<PublicBookSummary> findPublicBookSummaries(
            String pattern, Collection<String> isbns, Collection<String> isbnForms);

    @Query(
            """
                select r from Review r where r.bookId in :bookIds and r.visibility = :visibility and r.deleted = false
                order by r.publishedAt desc nulls last, cast(r.id as String) asc
            """)
    List<Review> findPublicReviews(
            Collection<UUID> bookIds, Visibility visibility, Pageable pageable);
}
