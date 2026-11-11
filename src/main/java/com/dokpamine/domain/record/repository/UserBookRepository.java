package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.UserBook;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface UserBookRepository extends JpaRepository<UserBook, UUID> {
    List<UserBook> findAllByUserIdAndDeletedFalse(UUID userId);

    // NFR-09: 삭제와 지연된 PUT을 직렬화해 동시에 도착해도 삭제를 되돌리지 못하게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select book from UserBook book where book.id = :id")
    Optional<UserBook> findByIdForUpdate(@Param("id") UUID id);
}
