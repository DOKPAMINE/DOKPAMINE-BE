package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.Visibility;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findAllByUserIdAndDeletedFalse(UUID userId);

    List<Review> findAllByVisibilityAndDeletedFalseOrderByPublishedAtDesc(Visibility visibility);
}
