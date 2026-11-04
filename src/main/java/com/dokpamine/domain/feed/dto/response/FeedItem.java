package com.dokpamine.domain.feed.dto.response;

import java.time.Instant;
import java.util.*;

public record FeedItem(
        UUID id,
        String nickname,
        String bookTitle,
        String bookAuthor,
        String genre,
        String oneLine,
        String body,
        Instant publishedAt,
        long likeCount,
        boolean likedByMe,
        boolean mine) {}
