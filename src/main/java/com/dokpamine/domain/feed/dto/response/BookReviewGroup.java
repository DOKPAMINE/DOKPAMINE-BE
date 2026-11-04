package com.dokpamine.domain.feed.dto.response;

import java.util.*;

public record BookReviewGroup(
        String bookKey, String bookTitle, String bookAuthor, String isbn, List<FeedItem> reviews) {}
