package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.Quote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {
    List<Quote> findAllByUserIdAndDeletedFalse(UUID userId);
}
