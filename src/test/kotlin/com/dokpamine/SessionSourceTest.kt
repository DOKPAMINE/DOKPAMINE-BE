package com.dokpamine

import com.dokpamine.record.SessionDto
import com.dokpamine.record.SessionSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionSourceTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: JsonMapper

    @ParameterizedTest
    @ValueSource(strings = ["startedAt", "endedAt", "activeSeconds"])
    fun `TIMER 세션의 시간을 바꿔 다시 보내면 MANUAL로 저장되고 랭킹에서 빠진다 (T09)`(field: String) {
        val token = signup()
        val original = session(putBook(token))
        mvc.patch("/api/me") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rankingOptIn":true}"""
        }.andExpect { status { isOk() } }

        assertEquals("TIMER", putSession(token, original).path("source").asString())
        assertEquals(600, myRanking(token).single().path("seconds").asInt())
        val modified = when (field) {
            "startedAt" -> original.copy(startedAt = original.startedAt.minusSeconds(60))
            "endedAt" -> original.copy(endedAt = original.endedAt.plusSeconds(60))
            else -> original.copy(activeSeconds = 660)
        }

        assertEquals("MANUAL", putSession(token, modified).path("source").asString())
        assertEquals("MANUAL", snapshotSessions(token).single().path("source").asString())
        assertEquals(0, myRanking(token).size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["123456123", "123456789", "999999999"])
    fun `같은 TIMER 세션을 그대로 재전송하면 TIMER로 남는다 (NFR-05)`(nanos: String) {
        val token = signup()
        val original = session(putBook(token), Instant.parse("2026-10-26T03:00:00.${nanos}Z"))

        repeat(3) {
            assertEquals("TIMER", putSession(token, original).path("source").asString())
        }

        val saved = snapshotSessions(token)
        assertEquals(1, saved.size)
        assertEquals(original.id.toString(), saved.single().path("id").asString())
        assertEquals("TIMER", saved.single().path("source").asString())
    }

    @Test
    fun `스냅샷으로 받은 마이크로초 시각으로 재전송해도 TIMER로 남는다 (NFR-05)`() {
        val token = signup()
        val original = session(putBook(token), Instant.parse("2026-10-26T03:00:00.123456789Z"))
        putSession(token, original)

        val pulled = snapshotSessions(token).single()
        val resent = original.copy(
            startedAt = Instant.parse(pulled.path("startedAt").asString()),
            endedAt = Instant.parse(pulled.path("endedAt").asString()),
        )

        assertEquals(Instant.parse("2026-10-26T03:00:00.123457Z"), resent.startedAt)
        assertEquals("TIMER", putSession(token, resent).path("source").asString())
        assertEquals("TIMER", snapshotSessions(token).single().path("source").asString())
    }

    @Test
    fun `TIMER 세션의 시각을 1마이크로초만 바꿔도 MANUAL로 저장된다 (T09)`() {
        val token = signup()
        val original = session(putBook(token), Instant.parse("2026-10-26T03:00:00.123456Z"))
        putSession(token, original)

        val modified = original.copy(endedAt = original.endedAt.plusNanos(1_000))
        assertEquals("MANUAL", putSession(token, modified).path("source").asString())
        assertEquals("MANUAL", snapshotSessions(token).single().path("source").asString())
    }

    @Test
    fun `검증에 실패한 시간 수정 요청은 저장된 TIMER를 바꾸지 않는다 (NFR-05)`() {
        val token = signup()
        val original = session(putBook(token))
        putSession(token, original)

        mvc.put("/api/sessions/${original.id}") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(original.copy(bookId = UUID.randomUUID(), activeSeconds = 660))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("BOOK_NOT_SYNCED") }
        }
        mvc.put("/api/sessions/${original.id}") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(original.copy(endedAt = original.startedAt.minusSeconds(1)))
        }.andExpect { status { isBadRequest() } }

        val saved = snapshotSessions(token).single()
        assertEquals("TIMER", saved.path("source").asString())
        assertEquals(600, saved.path("activeSeconds").asInt())
    }

    @Test
    fun `MANUAL 세션을 TIMER로 다시 보내도 MANUAL로 남는다 (T09)`() {
        val token = signup()
        val original = session(putBook(token)).copy(source = SessionSource.MANUAL)
        assertEquals("MANUAL", putSession(token, original).path("source").asString())

        assertEquals("MANUAL", putSession(token, original.copy(source = SessionSource.TIMER)).path("source").asString())
        assertEquals("MANUAL", snapshotSessions(token).single().path("source").asString())
    }

    @Test
    fun `시간이 그대로인 TIMER 세션에 MANUAL을 보내면 MANUAL로 저장되고 랭킹에서 빠진다 (T09)`() {
        val token = signup()
        val original = session(putBook(token))
        mvc.patch("/api/me") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rankingOptIn":true}"""
        }.andExpect { status { isOk() } }

        assertEquals("TIMER", putSession(token, original).path("source").asString())
        assertEquals(600, myRanking(token).single().path("seconds").asInt())

        val modified = original.copy(source = SessionSource.MANUAL)
        assertEquals("MANUAL", putSession(token, modified).path("source").asString())
        assertEquals("MANUAL", snapshotSessions(token).single().path("source").asString())
        assertEquals(0, myRanking(token).size)
    }

    @Test
    fun `TIMER 세션의 페이지만 바꾸면 TIMER로 남는다 (T09)`() {
        val token = signup()
        val original = session(putBook(token))
        putSession(token, original)

        assertEquals("TIMER", putSession(token, original.copy(startPage = 40, endPage = 60)).path("source").asString())
        val saved = snapshotSessions(token).single()
        assertEquals("TIMER", saved.path("source").asString())
        assertEquals(40, saved.path("startPage").asInt())
        assertEquals(60, saved.path("endPage").asInt())
    }

    @Test
    fun `TIMER 세션의 삭제 여부만 바꾸면 TIMER로 남는다 (T09)`() {
        val token = signup()
        val original = session(putBook(token))
        putSession(token, original)

        assertEquals("TIMER", putSession(token, original.copy(deleted = true)).path("source").asString())
        assertEquals(0, snapshotSessions(token).size)
        assertEquals("TIMER", putSession(token, original).path("source").asString())
        assertEquals("TIMER", snapshotSessions(token).single().path("source").asString())
    }

    @Test
    fun `다른 사용자는 세션의 시간과 source를 바꿀 수 없다 (NFR-05)`() {
        val owner = signup()
        val other = signup()
        val original = session(putBook(owner))
        putSession(owner, original)
        val modified = original.copy(bookId = putBook(other), activeSeconds = 660, source = SessionSource.MANUAL)

        mvc.put("/api/sessions/${original.id}") {
            bearer(other)
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(modified)
        }.andExpect { status { isForbidden() } }

        val saved = snapshotSessions(owner).single()
        assertEquals("TIMER", saved.path("source").asString())
        assertEquals(600, saved.path("activeSeconds").asInt())
    }

    private fun session(bookId: UUID, startedAt: Instant = Instant.parse("2026-10-26T03:00:00Z")) = SessionDto(
        id = UUID.randomUUID(), bookId = bookId, startedAt = startedAt, endedAt = startedAt.plusSeconds(600),
        activeSeconds = 600, startPage = 30, endPage = 50, source = SessionSource.TIMER, createdAt = startedAt,
    )

    private fun signup(): String {
        val body = mvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"${UUID.randomUUID()}@test.com","password":"password1","nickname":"독자"}"""
        }.andExpect { status { isCreated() } }.body()
        return json.readTree(body).path("accessToken").asString()
    }

    private fun putBook(token: String): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        mvc.put("/api/books/$id") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"$id","title":"우주를 건너는 문장","author":"윤서진","totalPages":240,"genre":"SF",
                "status":"READING","currentPage":30,"completedCount":0,"createdAt":"$now","updatedAt":"$now"}"""
        }.andExpect { status { isOk() } }
        return id
    }

    private fun putSession(token: String, dto: SessionDto): JsonNode = json.readTree(
        mvc.put("/api/sessions/${dto.id}") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(dto)
        }.andExpect { status { isOk() } }.body(),
    )

    private fun snapshotSessions(token: String): List<JsonNode> = json.readTree(
        mvc.get("/api/sync/snapshot") { bearer(token) }.andExpect { status { isOk() } }.body(),
    ).path("sessions").toList()

    private fun myRanking(token: String): List<JsonNode> = json.readTree(
        mvc.get("/api/ranking/weekly?weekStart=2026-10-26") { bearer(token) }.andExpect { status { isOk() } }.body(),
    ).path("entries").filter { it.path("me").asBoolean() }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(token: String) {
        header("Authorization", "Bearer $token")
    }

    private fun ResultActionsDsl.body(): String = andReturn().response.getContentAsString(Charsets.UTF_8)
}
