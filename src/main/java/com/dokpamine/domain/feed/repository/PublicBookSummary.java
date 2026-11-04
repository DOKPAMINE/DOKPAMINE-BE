package com.dokpamine.domain.feed.repository;

import java.util.*;

public interface PublicBookSummary {
    String getId();

    String getTitle();

    String getAuthor();

    String getIsbn();

    Object getLatestPublishedAt();

    String getLatestReviewId();
}
