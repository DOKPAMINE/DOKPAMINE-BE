package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.UserBook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UserBookRepository extends JpaRepository<UserBook, UUID> {
    List<UserBook> findAllByUserIdAndDeletedFalse(UUID userId);
}
