package com.dokpamine.domain.ranking.service;

import com.dokpamine.domain.ranking.dto.response.RankingEntry;
import com.dokpamine.domain.ranking.dto.response.RankingResponse;
import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.SessionSource;
import com.dokpamine.domain.record.repository.ReadingSessionRepository;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.domain.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RankingService {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final long DAILY_CAP_SECONDS = 180L * 60;
    public static final int DISPLAY_COUNT = 30;
    private final UserRepository users;
    private final ReadingSessionRepository sessions;

    public RankingService(UserRepository users, ReadingSessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    public RankingResponse weekly(UUID me, LocalDate weekStart) {
        LocalDate start =
                (weekStart == null ? LocalDate.now(KST) : weekStart)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<User> participants = users.findAllByRankingOptInTrue();
        Map<UUID, WeeklyTotal> totals =
                weeklySeconds(participants.stream().map(User::getId).toList(), start);
        List<User> sorted =
                participants.stream()
                        .filter(user -> totals.containsKey(user.getId()))
                        .sorted(
                                Comparator.comparingLong(
                                                (User user) -> totals.get(user.getId()).seconds())
                                        .reversed()
                                        .thenComparing(user -> totals.get(user.getId()).reachedAt())
                                        .thenComparing(user -> user.getId().toString()))
                        .toList();
        List<RankingEntry> entries = new ArrayList<>();
        int myIndex = -1;
        for (int i = 0; i < sorted.size(); i++) {
            User user = sorted.get(i);
            boolean mine = user.getId().equals(me);
            if (mine) myIndex = i;
            entries.add(
                    new RankingEntry(
                            i + 1,
                            user.getId(),
                            user.getNickname(),
                            totals.get(user.getId()).seconds(),
                            mine));
        }
        List<RankingEntry> around = List.of();
        if (myIndex >= DISPLAY_COUNT) {
            int from = Math.min(myIndex - DISPLAY_COUNT / 2, entries.size() - DISPLAY_COUNT);
            around = List.copyOf(entries.subList(from, from + DISPLAY_COUNT));
        }
        return new RankingResponse(
                start,
                participants.stream().anyMatch(user -> user.getId().equals(me)),
                entries.stream().limit(DISPLAY_COUNT).toList(),
                around);
    }

    private record WeeklyTotal(long seconds, Instant reachedAt) {}

    private Map<UUID, WeeklyTotal> weeklySeconds(List<UUID> ids, LocalDate start) {
        if (ids.isEmpty()) return Map.of();
        List<ReadingSession> selected =
                sessions.findRankingSessions(
                        ids,
                        SessionSource.TIMER,
                        start.atStartOfDay(KST).toInstant(),
                        start.plusWeeks(1).atStartOfDay(KST).toInstant());
        Map<UUID, List<ReadingSession>> groups =
                selected.stream().collect(Collectors.groupingBy(ReadingSession::getUserId));
        Map<UUID, WeeklyTotal> totals = new HashMap<>();
        for (var group : groups.entrySet()) {
            Map<LocalDate, Long> daily = new HashMap<>();
            long seconds = 0;
            Instant reachedAt = null;
            group.getValue()
                    .sort(
                            Comparator.comparing(ReadingSession::getEndedAt)
                                    .thenComparing(session -> session.getId().toString()));
            for (ReadingSession session : group.getValue()) {
                LocalDate day = session.getStartedAt().atZone(KST).toLocalDate();
                long counted = daily.getOrDefault(day, 0L);
                long added = Math.min(session.getActiveSeconds(), DAILY_CAP_SECONDS - counted);
                // FR-15: 상한 이후 세션과 0초 세션은 도달 시각에 영향을 주지 않는다.
                if (added > 0) {
                    daily.put(day, counted + added);
                    seconds += added;
                    reachedAt = session.getEndedAt();
                }
            }
            if (reachedAt != null) totals.put(group.getKey(), new WeeklyTotal(seconds, reachedAt));
        }
        return totals;
    }
}
