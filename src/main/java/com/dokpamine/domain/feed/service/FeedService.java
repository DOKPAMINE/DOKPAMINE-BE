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

import org.springframework.data.domain.PageRequest;
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

    public List<FeedItem> list(UUID me) {
        return items.toItems(
                reviews.findFeedReviews(Visibility.PUBLIC, PageRequest.of(0, 100)), me);
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
