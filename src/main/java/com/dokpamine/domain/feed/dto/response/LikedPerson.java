package com.dokpamine.domain.feed.dto.response;

import java.time.Instant;
import java.util.UUID;

/** 책 묶음 안에서 한 사람의 책 한 권. userBookId로 그 사람의 그 책 감상을 조회한다. */
public record LikedPerson(
        UUID authorId,
        String nickname,
        UUID userBookId,
        int likedCount,
        Instant lastLikedAt,
        boolean mine) {}
