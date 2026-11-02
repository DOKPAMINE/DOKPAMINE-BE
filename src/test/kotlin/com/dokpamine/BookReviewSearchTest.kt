package com.dokpamine

import com.dokpamine.feed.ReviewLike
import com.dokpamine.feed.ReviewLikeId
import com.dokpamine.feed.ReviewLikeRepository
import com.dokpamine.record.BookStatus
import com.dokpamine.record.Review
import com.dokpamine.record.ReviewRepository
import com.dokpamine.record.UserBook
import com.dokpamine.record.UserBookRepository
import com.dokpamine.record.Visibility
import com.dokpamine.user.User
import com.dokpamine.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookReviewSearchTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: JsonMapper
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var books: UserBookRepository
    @Autowired lateinit var reviews: ReviewRepository
    @Autowired lateinit var likes: ReviewLikeRepository

    private val now = Instant.parse("2026-11-01T12:00:00Z")

    @Test
    fun `데미안의 공개 두 건만 최신순으로 반환하고 공개 필드와 좋아요를 유지한다 (FR-16, NFR-09)`() {
        val me = user()
        val other = user()
        val book = book(me, "데미안", isbn = "9788937460449")
        val older = review(book, now.minusSeconds(60))
        val newer = review(book(other, "데미안", isbn = "9788937460449"), now)
        review(book, now.plusSeconds(60), Visibility.PRIVATE)
        likes.save(ReviewLike(ReviewLikeId(newer.id, me.id)))

        val groups = search(me, "데미안")
        assertEquals(1, groups.size())
        assertEquals("isbn:9788937460449", groups[0]["bookKey"].asString())
        val items = groups[0]["reviews"]
        assertEquals(listOf(newer.id.toString(), older.id.toString()), items.toList().map { it["id"].asString() })
        assertEquals(1, items[0]["likeCount"].asInt())
        assertEquals(true, items[0]["likedByMe"].asBoolean())
        assertFalse(items[0]["mine"].asBoolean())
        assertEquals(true, items[1]["mine"].asBoolean())

        val expectedFields = setOf(
            "id", "nickname", "bookTitle", "bookAuthor", "genre", "oneLine", "body",
            "publishedAt", "likeCount", "likedByMe", "mine",
        )
        items.toList().forEach { assertEquals(expectedFields, it.propertyNames().toSet()) }
        val detail = json.readTree(mvc.get("/api/feed/${newer.id}") {
            with(jwt().jwt { it.subject(me.id.toString()) })
        }.andExpect { status { isOk() } }.andReturn().response.getContentAsString(Charsets.UTF_8))
        assertEquals(detail, items[0])
    }

    @Test
    fun `ISBN이 같으면 제목 표기가 달라도 하나로 묶고 다른 ISBN과 ISBN 없는 책은 구분한다 (FR-16)`() {
        val me = user()
        val matching = review(book(me, "데미안", isbn = "9788937460449"), now.minusSeconds(10))
        val variant = review(book(user(), "Demian", isbn = "9788937460449"), now)
        review(book(me, "데미안", isbn = "9788937460456"), now.minusSeconds(20))
        review(book(me, "데미안"), now.minusSeconds(30))

        val groups = search(me, "데미안")
        assertEquals(3, groups.size())
        assertEquals(3, groups.toList().map { it["bookKey"].asString() }.toSet().size)
        assertEquals(
            listOf(variant.id.toString(), matching.id.toString()),
            groups[0]["reviews"].toList().map { it["id"].asString() },
        )
        assertEquals("Demian", groups[0]["bookTitle"].asString())
    }

    @Test
    fun `같은 책의 ISBN 10과 13 감상은 ISBN 13으로 한 묶음이 된다 (FR-16)`() {
        val me = user()
        val older = review(book(me, "데미안", isbn = "8937460440"), now.minusSeconds(10))
        val newer = review(book(user(), "Demian", isbn = "9788937460449"), now)

        val groups = search(me, "데미안")
        assertEquals(1, groups.size())
        assertEquals("isbn:9788937460449", groups[0]["bookKey"].asString())
        assertEquals("9788937460449", groups[0]["isbn"].asString())
        assertEquals(
            listOf(newer.id.toString(), older.id.toString()),
            groups[0]["reviews"].toList().map { it["id"].asString() },
        )
    }

    @Test
    fun `ISBN 없는 책은 공백과 대소문자를 정규화한 제목과 저자가 모두 같아야 묶인다 (FR-16)`() {
        val me = user()
        val older = review(book(me, "  My\t Book  ", "  Hermann   Hesse  "), now.minusSeconds(20))
        val newer = review(book(user(), "my book", "hermann\u00a0hesse", isbn = "  "), now)
        review(book(me, "MY BOOK", "다른 저자"), now.minusSeconds(10))
        review(book(me, "My Other Book", "Hermann Hesse"), now)

        val groups = search(me, "  MY\t\u00a0BOOK  ")
        assertEquals(2, groups.size())
        assertEquals(
            listOf(newer.id.toString(), older.id.toString()),
            groups[0]["reviews"].toList().map { it["id"].asString() },
        )
        assertEquals(true, groups[0]["bookKey"].asString().startsWith("text:"))
        assertEquals(groups[0]["bookKey"], search(me, "my book")[0]["bookKey"])
    }

    @Test
    fun `삭제 감상과 비공개 감상과 삭제 책은 본인 검색에서도 제외한다 (FR-16, NFR-09)`() {
        val me = user()
        val active = book(me, "숨긴 책", isbn = "9788937460449")
        review(active, now, Visibility.PRIVATE)
        review(active, now, deleted = true)
        review(book(user(), "숨긴 책"), now, Visibility.PRIVATE)
        review(book(me, "숨긴 책", deleted = true), now)
        assertEquals(0, search(me, "숨긴 책").size())

        review(book(user(), "Visible title", isbn = "9788937460449"), now)
        // 비공개 제목이 같은 ISBN의 공개 감상을 검색에 걸리게 해서도 안 된다.
        assertEquals(0, search(me, "숨긴 책").size())
        assertEquals(1, search(me, "Visible").size())
    }

    @Test
    fun `검색 결과가 없으면 200과 빈 목록을 반환한다 (FR-16)`() {
        assertEquals(0, search(user(), "없는 책").size())
    }

    @Test
    fun `검색어가 누락되거나 비어 있거나 공백뿐이면 400을 반환한다 (FR-16)`() {
        val me = user()
        listOf(null, "", " \t\n\u00a0 ").forEach { query ->
            mvc.get("/api/feed/books") {
                with(jwt().jwt { it.subject(me.id.toString()) })
                if (query != null) param("query", query)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("BAD_REQUEST") }
                jsonPath("$.message") { value("검색어를 입력해 주세요.") }
            }
        }
    }

    @Test
    fun `인증 없이 책별 공개 감상을 검색하면 401을 반환한다 (FR-16)`() {
        mvc.get("/api/feed/books") { param("query", "데미안") }.andExpect { status { isUnauthorized() } }
    }

    private fun search(me: User, query: String): JsonNode = json.readTree(
        mvc.get("/api/feed/books") {
            with(jwt().jwt { it.subject(me.id.toString()) })
            param("query", query)
        }.andExpect { status { isOk() } }.andReturn().response.getContentAsString(Charsets.UTF_8),
    )

    private fun user(): User = users.save(User(UUID.randomUUID(), "${UUID.randomUUID()}@test.com", "unused", "독자"))

    private fun book(
        user: User,
        title: String,
        author: String = "헤르만 헤세",
        isbn: String? = null,
        deleted: Boolean = false,
    ): UserBook = books.save(
        UserBook(
            id = UUID.randomUUID(), userId = user.id, isbn = isbn, title = title, author = author,
            publisher = null, totalPages = 200, genre = "소설", coverUrl = null, status = BookStatus.READING,
            currentPage = 20, completedCount = 0, completedAt = null, deleted = deleted,
            createdAt = now, updatedAt = now,
        ),
    )

    private fun review(
        book: UserBook,
        publishedAt: Instant,
        visibility: Visibility = Visibility.PUBLIC,
        deleted: Boolean = false,
    ): Review = reviews.save(
        Review(
            id = UUID.randomUUID(), userId = book.userId, bookId = book.id, oneLine = "한줄평", body = "독후감",
            visibility = visibility, publishedAt = publishedAt, deleted = deleted, createdAt = now, updatedAt = now,
        ),
    )
}
