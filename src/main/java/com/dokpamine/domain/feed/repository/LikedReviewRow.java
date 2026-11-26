package com.dokpamine.domain.feed.repository;

import java.time.Instant;
import java.util.UUID;

/** 내가 좋아요한 공개 감상 한 건과 그 감상이 연결된 책 정보. */
public interface LikedReviewRow {
    UUID getReviewId();

    Instant getLikedAt();

    UUID getUserBookId();

    UUID getAuthorId();

    String getIsbn();

    String getTitle();

    String getAuthor();

    String getCoverUrl();
}
