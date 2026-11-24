package com.dokpamine.domain.feed.service;

import com.dokpamine.domain.feed.dto.response.FeedItem;
import com.dokpamine.domain.feed.dto.response.LikeResponse;
import com.dokpamine.domain.feed.entity.ReviewLikeId;
import com.dokpamine.domain.feed.repository.ReviewLikeRepository;
import com.dokpamine.domain.feed.support.FeedItemMapper;
import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.Visibility;
import com.dokpamine.domain.record.repository.ReviewRepository;
import com.dokpamine.global.apiPayload.exception.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class FeedService {
    private final ReviewRepository reviews;
    private final ReviewLikeRepository likes;
    private final FeedItemMapper items;

    public FeedService(ReviewRepository reviews, ReviewLikeRepository likes, FeedItemMapper items) {
        this.reviews = reviews;
        this.likes = likes;
        this.items = items;
    }

    /** FR-08 사람의 책 하나를 박스 하나로 보고, 박스마다 대표 독후감을 최대 100개 반환한다. */
    public List<FeedItem> list(UUID me) {
        List<UUID> ids = reviews.findFeedBoxReviewIds(100).stream().map(UUID::fromString).toList();
        Map<UUID, Review> found = new HashMap<>();
        reviews.findAllById(ids).forEach(review -> found.put(review.getId(), review));
        return items.toItems(ids.stream().map(found::get).filter(Objects::nonNull).toList(), me);
    }

    /** FR-08 박스를 누르면 그 사람이 그 책에 쓴 공개 감상 전부를 보여 준다. */
    public List<FeedItem> bookReviews(UUID me, UUID userBookId) {
        List<FeedItem> mapped =
                items.toItems(reviews.findPublicBookReviews(userBookId, Visibility.PUBLIC), me);
        // NFR-09 공개 감상이 없으면 책이 있는지도 드러내지 않는다.
        if (mapped.isEmpty()) throw ApiException.notFound("감상");
        return mapped;
    }

    public FeedItem detail(UUID me, UUID id) {
        List<FeedItem> mapped = items.toItems(List.of(visibleReview(id, me)), me);
        if (mapped.isEmpty()) throw ApiException.notFound("감상");
        return mapped.getFirst();
    }

    @Transactional
    public LikeResponse like(UUID me, UUID id) {
        Review review = visibleReview(id, me);
        // FR-09 동시 재전송도 트랜잭션을 실패시키지 않도록 DB에서 중복을 처리한다.
        likes.insertIfAbsent(review.getId(), me);
        return new LikeResponse(likes.countByIdReviewId(id), true);
    }

    @Transactional
    public LikeResponse unlike(UUID me, UUID id) {
        visibleReview(id, me);
        likes.deleteById(new ReviewLikeId(id, me));
        return new LikeResponse(likes.countByIdReviewId(id), false);
    }

    private Review visibleReview(UUID id, UUID me) {
        Review review = reviews.findActiveReview(id).orElseThrow(() -> ApiException.notFound("감상"));
        if (review.getVisibility() != Visibility.PUBLIC && !review.getUserId().equals(me))
            throw ApiException.notFound("감상");
        return review;
    }
}
