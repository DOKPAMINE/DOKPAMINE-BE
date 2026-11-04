package com.dokpamine.domain.feed.repository;

import java.util.*;

public interface ReviewLikeCount {
    UUID getReviewId();

    long getLikeCount();
}
