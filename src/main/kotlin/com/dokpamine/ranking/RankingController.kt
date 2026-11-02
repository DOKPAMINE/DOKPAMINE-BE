package com.dokpamine.ranking

import com.dokpamine.config.userId
import com.dokpamine.record.ReadingSessionRepository
import com.dokpamine.record.SessionSource
import com.dokpamine.user.UserRepository
import com.dokpamine.user.User
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class RankingEntry(val rank: Int, val userId: UUID, val nickname: String, val seconds: Long, val me: Boolean)

data class RankingResponse(
    val weekStart: LocalDate,
    val participating: Boolean,
    val top: List<RankingEntry>,
    val around: List<RankingEntry>,
)

/**
 * 주간 독서왕 랭킹(FR-15, S-73~S-75).
 * - KST 월요일 00:00 ~ 다음 월요일 00:00 미만
 * - 타이머로 측정한 세션만, 하루 180분 상한
 * - 참여(rankingOptIn)한 사용자만 노출, 동점은 먼저 달성한 순서(FR-15)
 */
@RestController
@RequestMapping("/api/ranking")
class RankingController(
    private val users: UserRepository,
    private val sessions: ReadingSessionRepository,
) {

    @GetMapping("/weekly")
    @Transactional(readOnly = true)
    fun weekly(auth: Authentication, @RequestParam(required = false) weekStart: LocalDate?): RankingResponse {
        val me = auth.userId()
        val start = (weekStart ?: LocalDate.now(KST)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val participants = users.findAllByRankingOptInTrue()
        val totals = weeklySeconds(participants.map { it.id }, start)

        val sorted = participants
            .mapNotNull { user -> totals[user.id]?.let { user to it } }
            .sortedWith(compareByDescending<Pair<User, WeeklyTotal>> { it.second.seconds }
                .thenBy { it.second.reachedAt }.thenBy { it.first.id.toString() })

        val entries = sorted.mapIndexed { index, (user, total) ->
            RankingEntry(index + 1, user.id, user.nickname, total.seconds, user.id == me)
        }
        val myIndex = entries.indexOfFirst { it.me }
        val around = if (myIndex < DISPLAY_COUNT) emptyList() else {
            // FR-15: 본인 위 15명·아래 14명, 끝에서는 반대쪽으로 채워 본인 포함 30명을 유지한다.
            val from = (myIndex - DISPLAY_COUNT / 2).coerceAtMost(entries.size - DISPLAY_COUNT)
            entries.subList(from, from + DISPLAY_COUNT)
        }
        return RankingResponse(start, participants.any { it.id == me }, entries.take(DISPLAY_COUNT), around)
    }

    private data class WeeklyTotal(val seconds: Long, val reachedAt: Instant)

    private fun weeklySeconds(userIds: List<UUID>, weekStart: LocalDate): Map<UUID, WeeklyTotal> {
        if (userIds.isEmpty()) return emptyMap()
        val from = weekStart.atStartOfDay(KST).toInstant()
        val to = weekStart.plusWeeks(1).atStartOfDay(KST).toInstant()
        return sessions
            .findAllByUserIdInAndSourceAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                userIds, SessionSource.TIMER, from, to,
            )
            .groupBy { it.userId }
            .mapNotNull { (userId, list) ->
                val daily = mutableMapOf<LocalDate, Long>()
                var seconds = 0L
                var reachedAt: Instant? = null
                // FR-15: 종료 시각 순으로 일별 상한까지 인정한다. 도달 시각은 주간 누적을 마지막으로
                // 늘린 세션의 endedAt이다. 일시정지 구간을 모르므로 세션 중간 시각을 추정하지 않는다.
                // 상한 이후 세션·0초 세션과 서버 동기화 시각(createdAt)은 도달 시각에 영향을 주지 않는다.
                list.sortedWith(compareBy({ it.endedAt }, { it.id.toString() })).forEach { session ->
                    val day = session.startedAt.atZone(KST).toLocalDate()
                    val counted = daily[day] ?: 0L
                    val added = minOf(session.activeSeconds.toLong(), DAILY_CAP_SECONDS - counted)
                    if (added > 0) {
                        daily[day] = counted + added
                        seconds += added
                        reachedAt = session.endedAt
                    }
                }
                reachedAt?.let { userId to WeeklyTotal(seconds, it) }
            }
            .toMap()
    }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val DAILY_CAP_SECONDS = 180L * 60
        const val DISPLAY_COUNT = 30
    }
}
