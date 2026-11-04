package com.dokpamine.domain.record.repository;

import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.SessionSource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.*;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, UUID> {
    List<ReadingSession> findAllByUserIdAndDeletedFalse(UUID userId);

    List<ReadingSession>
            findAllByUserIdInAndSourceAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                    Collection<UUID> userIds, SessionSource source, Instant from, Instant to);
}
