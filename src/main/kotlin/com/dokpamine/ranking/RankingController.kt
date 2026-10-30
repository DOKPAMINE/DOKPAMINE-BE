package com.dokpamine.ranking

import com.dokpamine.config.userId
import com.dokpamine.record.ReadingSessionRepository
import com.dokpamine.record.SessionSource
import com.dokpamine.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class RankingEntry(val rank: Int, val userId: UUID, val nickname: String, val seconds: Long, val me: Boolean)

data class RankingResponse(
    val weekStart: LocalDate,
    val participating: Boolean,
    val entries: List<RankingEntry>,
)

/**
 * 주간 독서왕 랭킹(PRD L01~L05).
 * - KST 월요일 00:00 ~ 다음 월요일 00:00 미만
 * - 타이머로 측정한 세션만, 하루 180분 상한
 * - 참여(rankingOptIn)한 사용자만 노출, 동점은 1, 2, 2, 4 방식
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
            .map { it to (totals[it.id] ?: 0L) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        var rank = 0
        var previous: Long? = null
        val entries = sorted.mapIndexed { index, (user, seconds) ->
            if (seconds != previous) rank = index + 1
            previous = seconds
            RankingEntry(rank, user.id, user.nickname, seconds, user.id == me)
        }
        return RankingResponse(start, participants.any { it.id == me }, entries)
    }

    private fun weeklySeconds(userIds: List<UUID>, weekStart: LocalDate): Map<UUID, Long> {
        if (userIds.isEmpty()) return emptyMap()
        val from = weekStart.atStartOfDay(KST).toInstant()
        val to = weekStart.plusWeeks(1).atStartOfDay(KST).toInstant()
        return sessions
            .findAllByUserIdInAndSourceAndDeletedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                userIds, SessionSource.TIMER, from, to,
            )
            .groupBy { it.userId }
            .mapValues { (_, list) ->
                list.groupBy { it.startedAt.atZone(KST).toLocalDate() }
                    .values
                    .sumOf { day -> minOf(day.sumOf { it.activeSeconds.toLong() }, DAILY_CAP_SECONDS) }
            }
    }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val DAILY_CAP_SECONDS = 180L * 60
    }
}
