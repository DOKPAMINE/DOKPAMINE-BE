package com.dokpamine.domain.feed.dto.response;

import java.time.Instant;
import java.util.List;

public record LikedBookGroup(
        String bookKey,
        String isbn,
        String bookTitle,
        String bookAuthor,
        String coverUrl,
        Instant lastLikedAt,
        List<LikedPerson> people) {}
