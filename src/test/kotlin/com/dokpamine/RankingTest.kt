package com.dokpamine

import com.dokpamine.record.BookStatus
import com.dokpamine.record.ReadingSession
import com.dokpamine.record.ReadingSessionRepository
import com.dokpamine.record.SessionSource
import com.dokpamine.record.UserBook
import com.dokpamine.record.UserBookRepository
import com.dokpamine.user.User
import com.dokpamine.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RankingTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: JsonMapper
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var books: UserBookRepository
    @Autowired lateinit var sessions: ReadingSessionRepository

    private val monday = Instant.parse("2026-10-25T15:00:00Z")

    @Test
    fun `300분 A와 200분 B C는 먼저 도달한 B부터 연속 순위를 받는다 (FR-15)`() {
        val a = user("A")
        val c = user("C")
        val b = user("B")
        session(a, 180 * 60)
        session(a, 120 * 60, monday.plusSeconds(86400))
        session(c, 100 * 60)
        session(c, 100 * 60, monday.plusSeconds(90000))
        session(b, 180 * 60)
        session(b, 20 * 60, monday.plusSeconds(86400))
        val response = ranking(b)
        assertEquals(listOf("A", "B", "C"), response["top"].toList().map { it["nickname"].asString() })
        assertEquals(listOf(1, 2, 3), response["top"].toList().map { it["rank"].asInt() })
        assertEquals(listOf(18000L, 12000L, 12000L), response["top"].toList().map { it["seconds"].asLong() })
        assertEquals(true, response["top"][1]["me"].asBoolean())
        assertEquals(0, response["around"].size())
        assertEquals(setOf("weekStart", "participating", "top", "around"), response.propertyNames().toSet())
    }

    @Test
    fun `45명 중 38위는 상위 30명과 자신 포함 주변 30명을 받는다 (FR-15)`() {
        val participants = (1..45).map { rank -> user("독자 $rank").also { session(it, 1000 - rank) } }
        val response = ranking(participants[37])
        assertEquals((1..30).toList(), response["top"].toList().map { it["rank"].asInt() })
        assertEquals((16..45).toList(), response["around"].toList().map { it["rank"].asInt() })
        assertEquals(listOf(38), response["around"].toList().filter { it["me"].asBoolean() }.map { it["rank"].asInt() })
        assertEquals(0, ranking(participants[29])["around"].size())
        assertEquals((16..45).toList(), ranking(participants[30])["around"].toList().map { it["rank"].asInt() })
        assertEquals((16..45).toList(), ranking(participants.last())["around"].toList().map { it["rank"].asInt() })
        assertEquals(0, ranking(user("비참여", false))["around"].size())
    }

    @Test
    fun `주변 순위는 가운데에서 위 15명 아래 14명을 포함한다 (FR-15)`() {
        val participants = (1..70).map { rank -> user("독자 $rank").also { session(it, 1000 - rank) } }
        assertEquals((23..52).toList(), ranking(participants[37])["around"].toList().map { it["rank"].asInt() })
    }

    @Test
    fun `수동 삭제 비참여 세션은 빠지고 참여 중단은 즉시 반영된다 (FR-15)`() {
        val active = user("참여")
        val hidden = user("비참여", false)
        val manual = user("수동")
        session(active, 200 * 60)
        session(hidden, 180 * 60)
        session(manual, 180 * 60, source = SessionSource.MANUAL)
        session(manual, 180 * 60, deleted = true)
        val response = ranking(hidden)
        assertEquals(false, response["participating"].asBoolean())
        assertEquals(listOf("참여"), response["top"].toList().map { it["nickname"].asString() })
        assertEquals(10800, response["top"][0]["seconds"].asInt())
        assertEquals(true, ranking(manual)["participating"].asBoolean())
        mvc.patch("/api/me") {
            with(jwt().jwt { it.subject(active.id.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = """{"rankingOptIn":false}"""
        }.andExpect { status { isOk() } }
        val stopped = ranking(active)
        assertEquals(false, stopped["participating"].asBoolean())
        assertEquals(0, stopped["top"].size())
        assertEquals(0, stopped["around"].size())
    }

    @Test
    fun `상한 이후와 0초 세션은 도달 시각을 늦추지 않고 동기화 시각은 무관하다 (FR-15)`() {
        val early = user("먼저")
        val later = user("나중")
        session(early, 180 * 60, createdAt = monday.plusSeconds(20 * 86400))
        session(early, 60, monday.plusSeconds(20000))
        session(early, 0, monday.plusSeconds(90000))
        session(later, 180 * 60, monday.plusSeconds(60), createdAt = monday)
        assertEquals(listOf("먼저", "나중"), ranking(later)["top"].toList().map { it["nickname"].asString() })
    }

    @Test
    fun `주 경계는 KST 시작 시각으로 판정하고 도달 시각까지 같으면 UUID로 정렬한다 (FR-15)`() {
        val first = user("첫째", id = UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val second = user("둘째", id = UUID.fromString("80000000-0000-0000-0000-000000000001"))
        session(second, 60)
        session(first, 60)
        session(first, 600, monday.minusSeconds(1))
        session(first, 600, monday.plusSeconds(7 * 86400))
        val response = ranking(first, "2026-10-28")
        assertEquals("2026-10-26", response["weekStart"].asString())
        assertEquals(listOf("첫째", "둘째"), response["top"].toList().map { it["nickname"].asString() })
        assertEquals(listOf(60, 60), response["top"].toList().map { it["seconds"].asInt() })
    }

    @Test
    fun `빈 주간 랭킹은 빈 목록이고 인증이 없으면 거절한다 (FR-15)`() {
        val response = ranking(user("기록 없음"))
        assertEquals(0, response["top"].size())
        assertEquals(0, response["around"].size())
        mvc.get("/api/ranking/weekly").andExpect { status { isUnauthorized() } }
    }

    private fun ranking(me: User, weekStart: String = "2026-10-26"): JsonNode = json.readTree(
        mvc.get("/api/ranking/weekly") {
            with(jwt().jwt { it.subject(me.id.toString()) })
            param("weekStart", weekStart)
        }.andExpect { status { isOk() } }.andReturn().response.getContentAsString(Charsets.UTF_8),
    )

    private fun user(nickname: String, optIn: Boolean = true, id: UUID = UUID.randomUUID()): User =
        users.save(User(id, "$id@test.com", "unused", nickname, optIn))

    private fun session(
        user: User,
        seconds: Int,
        start: Instant = monday,
        source: SessionSource = SessionSource.TIMER,
        deleted: Boolean = false,
        createdAt: Instant = monday.plusSeconds(14 * 86400),
    ): ReadingSession {
        val book = books.save(UserBook(
            UUID.randomUUID(), user.id, null, "랭킹 책", "저자", null, 200, "소설", null,
            BookStatus.READING, 0, 0, null, false, monday, monday,
        ))
        return sessions.save(ReadingSession(
            UUID.randomUUID(), user.id, book.id, start, start.plusSeconds(seconds.toLong()), seconds,
            0, null, source, deleted, createdAt,
        ))
    }
}
