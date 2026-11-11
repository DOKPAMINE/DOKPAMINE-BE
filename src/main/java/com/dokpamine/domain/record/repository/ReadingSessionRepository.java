package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.SessionSource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.*;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, UUID> {
    List<ReadingSession> findAllByUserIdAndDeletedFalse(UUID userId);

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
