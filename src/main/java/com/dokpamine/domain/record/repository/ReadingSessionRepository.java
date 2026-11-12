package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.SessionSource;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ReadingSession session where session.id = :id")
    Optional<ReadingSession> findByIdForUpdate(@Param("id") UUID id);

    List<ReadingSession> findAllByUserIdAndDeletedFalse(UUID userId);

    @Query("select count(s) > 0 from ReadingSession s where s.bookId = :bookId"
            + " and s.deleted = false and (s.startPage > :totalPages or s.endPage > :totalPages)")
    boolean existsOutsidePageRange(UUID bookId, int totalPages);

    @Query(
            """
                select s from ReadingSession s join UserBook b on b.id = s.bookId
                where s.userId in :userIds and s.source = :source
                  and s.deleted = false and b.deleted = false
                  and s.startedAt >= :from and s.startedAt < :to
            """)
    List<ReadingSession> findRankingSessions(
            Collection<UUID> userIds, SessionSource source, Instant from, Instant to);
}
