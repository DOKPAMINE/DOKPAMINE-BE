package com.dokpamine

import com.dokpamine.catalog.Isbn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.delete
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: JsonMapper

    @Test
    fun `같은 세션을 여러 번 보내도 서버에는 한 건만 저장된다 (NFR-05)`() {
        val token = signup()
        val bookId = putBook(token)
        val sessionIds = List(3) { UUID.randomUUID() }

        repeat(2) {
            sessionIds.forEach { id -> putSession(token, id, bookId, Instant.now().minusSeconds(600), 600) }
        }

        val snapshot = json.readTree(
            mvc.get("/api/sync/snapshot") { bearer(token) }.andExpect { status { isOk() } }.body(),
        )
        assertEquals(3, snapshot.path("sessions").size())
    }

    @Test
    fun `비공개 감상은 다른 사용자에게 보이지 않고 공개 전환 후에만 보인다 (FR-08, NFR-09)`() {
        val a = signup()
        val b = signup()
        val bookId = putBook(a)
        val reviewId = UUID.randomUUID()

        putReview(a, reviewId, bookId, "PRIVATE")
        mvc.get("/api/feed/$reviewId") { bearer(b) }.andExpect { status { isNotFound() } }

        putReview(a, reviewId, bookId, "PUBLIC")
        mvc.get("/api/feed/$reviewId") { bearer(b) }.andExpect { status { isOk() } }

        putReview(a, reviewId, bookId, "PRIVATE")
        mvc.get("/api/feed/$reviewId") { bearer(b) }.andExpect { status { isNotFound() } }

        // 다른 사람의 기록은 덮어쓸 수 없다.
        mvc.put("/api/books/$bookId") {
            bearer(b)
            contentType = MediaType.APPLICATION_JSON
            content = bookJson(bookId)
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `좋아요는 중복 요청해도 1만 증가하고 취소하면 원래대로 돌아간다 (FR-09)`() {
        val a = signup()
        val b = signup()
        val bookId = putBook(a)
        val reviewId = UUID.randomUUID()
        putReview(a, reviewId, bookId, "PUBLIC")

        repeat(2) { mvc.post("/api/feed/$reviewId/like") { bearer(b) }.andExpect { status { isOk() } } }
        val liked = json.readTree(mvc.get("/api/feed/$reviewId") { bearer(b) }.body())
        assertEquals(1, liked.path("likeCount").asInt())

        mvc.delete("/api/feed/$reviewId/like") { bearer(b) }.andExpect { status { isOk() } }
        val unliked = json.readTree(mvc.get("/api/feed/$reviewId") { bearer(b) }.body())
        assertEquals(0, unliked.path("likeCount").asInt())
    }

    @Test
    fun `랭킹은 참여자만 하루 180분까지만 집계한다 (FR-15)`() {
        val a = signup()
        val bookId = putBook(a)
        mvc.patch("/api/me") {
            bearer(a)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rankingOptIn":true}"""
        }.andExpect { status { isOk() } }

        val kst = ZoneId.of("Asia/Seoul")
        val mondayNoon = ZonedDateTime.now(kst).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .withHour(12).withMinute(0).withSecond(0).withNano(0).toInstant()
        putSession(a, UUID.randomUUID(), bookId, mondayNoon, 200 * 60)

        val ranking = json.readTree(mvc.get("/api/ranking/weekly") { bearer(a) }.body())
        val mine = ranking.path("top").first { it.path("me").asBoolean() }
        assertEquals(180L * 60, mine.path("seconds").asLong())
    }

    @Test
    fun `ISBN 은 체크섬을 검증하고 13자리로 정규화한다`() {
        assertEquals("9788937460449", Isbn.normalize("978-89-374-6044-9"))
        assertEquals("9788937460449", Isbn.normalize("8937460440"))
        assertNull(Isbn.normalize("9788937460440"))
    }

    // --- helpers ---

    private fun signup(): String {
        val email = "u${UUID.randomUUID().toString().take(8)}@test.com"
        val body = mvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password1","nickname":"독자"}"""
        }.andExpect { status { isCreated() } }.body()
        return json.readTree(body).path("accessToken").asString()
    }

    private fun bookJson(id: UUID): String {
        val now = Instant.now()
        return """{"id":"$id","title":"우주를 건너는 문장","author":"윤서진","totalPages":240,"genre":"SF",
            "status":"READING","currentPage":30,"completedCount":0,"createdAt":"$now","updatedAt":"$now"}"""
    }

    private fun putBook(token: String): UUID {
        val id = UUID.randomUUID()
        mvc.put("/api/books/$id") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = bookJson(id)
        }.andExpect { status { isOk() } }
        return id
    }

    private fun putSession(token: String, id: UUID, bookId: UUID, startedAt: Instant, seconds: Int) {
        mvc.put("/api/sessions/$id") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"$id","bookId":"$bookId","startedAt":"$startedAt","endedAt":"${startedAt.plusSeconds(seconds.toLong())}",
                "activeSeconds":$seconds,"startPage":30,"endPage":50,"source":"TIMER","createdAt":"$startedAt"}"""
        }.andExpect { status { isOk() } }
    }

    private fun putReview(token: String, id: UUID, bookId: UUID, visibility: String) {
        val now = Instant.now()
        mvc.put("/api/reviews/$id") {
            bearer(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"$id","bookId":"$bookId","oneLine":"낯선 우주에서 발견한 다정함.","visibility":"$visibility",
                "createdAt":"$now","updatedAt":"$now"}"""
        }.andExpect { status { isOk() } }
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(token: String) {
        header("Authorization", "Bearer $token")
    }

    private fun ResultActionsDsl.body(): String = andReturn().response.getContentAsString(Charsets.UTF_8)
}
