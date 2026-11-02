package com.dokpamine

import com.dokpamine.feed.BookReviewSearchRepository
import com.dokpamine.feed.ReviewLike
import com.dokpamine.feed.ReviewLikeId
import com.dokpamine.feed.ReviewLikeRepository
import com.dokpamine.feed.nativePublishedAtToInstant
import com.dokpamine.record.BookStatus
import com.dokpamine.record.Review
import com.dokpamine.record.ReviewRepository
import com.dokpamine.record.UserBook
import com.dokpamine.record.UserBookRepository
import com.dokpamine.record.Visibility
import com.dokpamine.user.User
import com.dokpamine.user.UserRepository
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
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
    @Autowired lateinit var entityManager: EntityManager
    @Autowired lateinit var searchBooks: BookReviewSearchRepository

    private val now = Instant.parse("2026-11-01T12:00:00Z")

    @Test
    fun `데미안의 공개 두 건만 최신순으로 반환하고 공개 필드와 좋아요를 유지한다 (FR-16, NFR-09)`() {
        val me = user()
        val other = user()
        val book = book(me, "데미안", isbn = "9788937460449")
        val older = review(book, now.minusSeconds(60))
        val newer = review(book(other, "데미안", isbn = "9788937460449"), now)
        review(book(user(), "데미안", isbn = "9788937460449"), now.plusSeconds(60), Visibility.PRIVATE)
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
        review(book(user(), "데미안", isbn = "9788937460456"), now.minusSeconds(20))
        review(book(user(), "데미안"), now.minusSeconds(30))

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
    fun `공개 시각이 같으면 UUID 문자열 순으로 감상과 묶음을 정렬하고 시각 없음은 마지막이다 (FR-16)`() {
        val me = user()
        val firstBook = book(me, "동점 책", isbn = "9788937460449")
        val firstId = UUID.fromString("00000000-0000-0000-8000-000000000000")
        val secondId = UUID.fromString("00000000-0000-0000-9000-000000000000")
        val thirdId = UUID.fromString("80000000-0000-0000-0000-000000000000")
        review(firstBook, now, id = secondId)
        review(book(user(), "동점 책", isbn = "9788937460449"), now, id = firstId)
        review(book(user(), "동점 책", isbn = "9788937460456"), now, id = thirdId)
        review(book(user(), "동점 책", author = "시각 없음"), now).also { it.publishedAt = null }

        val groups = search(me, "동점 책")
        assertEquals(listOf(firstId.toString(), secondId.toString()), groups[0]["reviews"].toList().map { it["id"].asString() })
        assertEquals(thirdId.toString(), groups[1]["reviews"][0]["id"].asString())
        assertEquals(true, groups[2]["reviews"][0]["publishedAt"].isNull)
    }

    @Test
    fun `ISBN 없는 책은 공백과 대소문자를 정규화한 제목과 저자가 모두 같아야 묶인다 (FR-16)`() {
        val me = user()
        val older = review(book(me, "  My\t Book  ", "  Hermann   Hesse  "), now.minusSeconds(20))
        val newer = review(book(user(), "my book", "hermann\u00a0hesse", isbn = "  "), now)
        review(book(user(), "MY BOOK", "다른 저자"), now.minusSeconds(10))
        review(book(user(), "My Other Book", "Hermann Hesse"), now)

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
        review(book(user(), "숨긴 책", isbn = "9788937460449"), now, deleted = true)
        review(book(user(), "숨긴 책"), now, Visibility.PRIVATE)
        review(book(user(), "숨긴 책", deleted = true), now)
        assertEquals(0, search(me, "숨긴 책").size())

        review(book(user(), "Visible title", isbn = "9788937460449"), now)
        // 비공개 제목이 같은 ISBN의 공개 감상을 검색에 걸리게 해서도 안 된다.
        assertEquals(0, search(me, "숨긴 책").size())
        assertEquals(1, search(me, "Visible").size())
    }

    @Test
    fun `검색은 최신 20개 묶음과 묶음마다 최신 감상 100개까지만 반환한다 (FR-16)`() {
        val me = user()
        val popularReviews = (0..100).map {
            review(book(user(), "상한 검색", author = "인기 저자"), now.plusSeconds(it.toLong()))
        }
        val otherReviews = (0..20).map { index ->
            review(book(user(), "상한 검색", author = "저자 $index"), now.minusSeconds(index + 1L))
        }

        val groups = search(me, "상한 검색")
        assertEquals(20, groups.size())
        assertEquals(popularReviews.reversed().take(100).map { it.id.toString() }, groups[0]["reviews"].toList().map { it["id"].asString() })
        assertEquals(otherReviews.take(19).map { it.id.toString() }, groups.toList().drop(1).map { it["reviews"][0]["id"].asString() })
    }

    @Test
    fun `제목 후보는 최신 공개순 500건까지만 읽고 초과 책은 검색에서 제외한다 (FR-16)`() {
        val me = user()
        val candidates = (0..500).map { index ->
            book(user(), "후보 상한 검색", author = "저자 $index").also {
                review(it, now.minusSeconds(index.toLong()))
            }
        }
        val excluded = candidates.last()
        // 비공개·삭제 감상이 최신이어도 공개 후보의 우선순위를 올리지 않는다.
        review(book(user(), "후보 상한 검색", author = "저자 500"), now.plusSeconds(1000), Visibility.PRIVATE)
        review(book(user(), "후보 상한 검색", author = "저자 500"), now.plusSeconds(1000), deleted = true)
        entityManager.flush()
        entityManager.clear()

        val projected = searchBooks.findTitleCandidates("%후보%상한%검색%", Visibility.PUBLIC, PageRequest.of(0, 500))
        assertEquals(candidates.take(500).map { it.id }, projected.map { it.id })
        projected.forEachIndexed { index, candidate ->
            assertEquals(candidates[index].title, candidate.title)
            assertEquals(candidates[index].author, candidate.author)
            assertEquals(candidates[index].isbn, candidate.isbn)
        }

        val groups = search(me, "후보 상한 검색").toList()
        assertEquals(20, groups.size)
        assertFalse(groups.any { it["bookAuthor"].asString() == excluded.author })
        val expectedAuthors = candidates.take(500).sortedBy { it.author.removePrefix("저자 ").toInt() }
            .take(20).map { it.author }
        assertEquals(expectedAuthors, groups.map { it["bookAuthor"].asString() })
    }

    @Test
    fun `요약 조회는 제목과 ISBN으로 좁히고 다른 제목의 ISBN 10 사본을 유지한다 (FR-16)`() {
        val me = user()
        val matching = review(book(me, "찾을 제목", isbn = "9788937460449"), now.minusSeconds(20))
        val variant = review(book(user(), "Different title", isbn = "8937-46044-0"), now)
        val textMatch = review(book(user(), "찾을 제목", author = "ISBN 없음"), now.minusSeconds(10))
        review(book(user(), "관계없는 제목", isbn = "9788937460456"), now.plusSeconds(100))
        review(book(user(), "Different private", isbn = "8937460440"), now, Visibility.PRIVATE)
        review(book(user(), "Different deleted", isbn = "8937460440", deleted = true), now)
        entityManager.flush()
        entityManager.clear()

        val summaries = searchBooks.findPublicBookSummaries("%찾을%제목%", listOf("9788937460449"), listOf("9788937460449", "8937460440"))
        assertEquals(setOf(matching.bookId, variant.bookId, textMatch.bookId), summaries.map { UUID.fromString(it.id) }.toSet())
        val groups = search(me, "찾을 제목")
        assertEquals(2, groups.size())
        assertEquals(listOf(variant.id.toString(), matching.id.toString()), groups[0]["reviews"].toList().map { it["id"].asString() })
    }

    @Test
    fun `정규화할 수 없는 ISBN도 같은 값인 다른 제목의 사본을 유지한다 (FR-16)`() {
        val me = user()
        val matching = review(book(me, "검증 불가 책", isbn = "unknown"), now.minusSeconds(10))
        val variant = review(book(user(), "Unknown title", isbn = " unknown "), now)
        val groups = search(me, "검증 불가")
        assertEquals(1, groups.size())
        assertEquals(listOf(variant.id.toString(), matching.id.toString()), groups[0]["reviews"].toList().map { it["id"].asString() })
    }

    @Test
    fun `여러 책 묶음에서도 좋아요를 두 번만 조회하고 SQL 실행 수 상한을 유지한다 (FR-16)`() {
        val me = user()
        val other = user()
        val entries = (0..2).flatMap { group ->
            (0..29).map { review(book(user(), "배치 좋아요 $group"), now.minusSeconds(it.toLong())) }
        }
        entries.forEachIndexed { index, review ->
            likes.save(ReviewLike(ReviewLikeId(review.id, other.id)))
            if (index % 2 == 0) likes.save(ReviewLike(ReviewLikeId(review.id, me.id)))
        }
        entityManager.flush()
        entityManager.clear()
        val sessionFactory: SessionFactory = entityManager.entityManagerFactory.unwrap(SessionFactory::class.java)
        val statistics: Statistics = sessionFactory.statistics
        val wasEnabled = statistics.isStatisticsEnabled
        statistics.isStatisticsEnabled = true
        try {
            listOf("배치 좋아요 0" to 1, "배치 좋아요" to 3).forEach { (query, groupCount) ->
                entityManager.clear()
                statistics.clear()
                val groups = search(me, query).toList()
                assertEquals(groupCount, groups.size)
                groups.forEach { group ->
                    val items = group["reviews"].toList()
                    assertEquals(30, items.size)
                    items.forEachIndexed { index, item ->
                        assertEquals(if (index % 2 == 0) 2 else 1, item["likeCount"].asInt())
                        assertEquals(index % 2 == 0, item["likedByMe"].asBoolean())
                    }
                }
                val queries: Array<String> = statistics.queries
                val likeExecutions = queries.filter { it.contains("ReviewLike") }
                    .sumOf { statistics.getQueryStatistics(it).executionCount }
                assertEquals(2L, likeExecutions, "좋아요 쿼리 실행 수: $query")
                val executions = queries.sumOf { statistics.getQueryStatistics(it).executionCount }
                // 후보·요약 2회, 묶음별 감상 조회, 책·사용자·좋아요 배치 조회 4회다.
                val limit = groupCount + 6L
                assertTrue(executions <= limit, "${groupCount}개 묶음 쿼리 실행 수: $executions")
                assertTrue(statistics.prepareStatementCount <= limit, "${groupCount}개 묶음 SQL 수: ${statistics.prepareStatementCount}")
            }
        } finally {
            statistics.isStatisticsEnabled = wasEnabled
        }
    }

    @Test
    fun `검색어의 LIKE 특수문자는 일반 제목 문자로 검색한다 (FR-16)`() {
        val me = user()
        review(book(me, "100%_! 책"), now)
        review(book(user(), "100퍼센트 다른 책"), now)
        assertEquals(1, search(me, "100%_!").size())
    }

    @Test
    fun `공용 매퍼를 쓰는 피드와 상세와 검색은 같은 공개 응답과 기존 비공개 권한을 유지한다 (FR-08, FR-16, NFR-09)`() {
        val me = user()
        val other = user()
        val book = book(other, "공용 매퍼 책")
        val older = review(book, now.minusSeconds(10))
        val newer = review(book(user(), "공용 매퍼 책"), now)
        val privateReview = review(book(me, "공용 매퍼 책"), now.plusSeconds(20), Visibility.PRIVATE)
        val deletedBookReview = review(book(user(), "공용 매퍼 책", deleted = true), now.plusSeconds(30))
        likes.save(ReviewLike(ReviewLikeId(newer.id, me.id)))
        likes.save(ReviewLike(ReviewLikeId(newer.id, other.id)))

        val feed = json.readTree(mvc.get("/api/feed") {
            with(jwt().jwt { it.subject(me.id.toString()) })
        }.andExpect { status { isOk() } }.andReturn().response.getContentAsString(Charsets.UTF_8))
            .toList().filter { it["bookTitle"].asString() == "공용 매퍼 책" }
        assertEquals(listOf(newer.id.toString(), older.id.toString()), feed.map { it["id"].asString() })
        assertEquals(search(me, "공용 매퍼 책")[0]["reviews"].toList(), feed)
        feed.forEach { item ->
            val detail = json.readTree(mvc.get("/api/feed/${item["id"].asString()}") {
                with(jwt().jwt { it.subject(me.id.toString()) })
            }.andExpect { status { isOk() } }.andReturn().response.getContentAsString(Charsets.UTF_8))
            assertEquals(item, detail)
        }
        mvc.get("/api/feed/${privateReview.id}") {
            with(jwt().jwt { it.subject(me.id.toString()) })
        }.andExpect { status { isOk() } }
        mvc.get("/api/feed/${privateReview.id}") {
            with(jwt().jwt { it.subject(other.id.toString()) })
        }.andExpect { status { isNotFound() } }
        mvc.get("/api/feed/${deletedBookReview.id}") {
            with(jwt().jwt { it.subject(me.id.toString()) })
        }.andExpect { status { isNotFound() } }
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
        id: UUID = UUID.randomUUID(),
    ): Review = reviews.save(
        Review(
            id = id, userId = book.userId, bookId = book.id, oneLine = "한줄평", body = "독후감",
            visibility = visibility, publishedAt = publishedAt, deleted = deleted, createdAt = now, updatedAt = now,
        ),
    )
}

class NativePublishedAtConversionTest {
    private val expected = Instant.parse("2026-11-01T12:00:00.123456Z")

    @Test
    fun `네이티브 공개 시각의 Instant 반환값을 보존한다 (FR-16)`() {
        assertEquals(expected, nativePublishedAtToInstant(expected))
    }

    @Test
    fun `네이티브 공개 시각의 OffsetDateTime 반환값을 UTC 순간으로 변환한다 (FR-16)`() {
        val value = OffsetDateTime.parse("2026-11-01T21:00:00.123456+09:00")
        assertEquals(expected, nativePublishedAtToInstant(value))
    }

    @Test
    fun `네이티브 공개 시각의 Timestamp 반환값을 정밀도 손실 없이 변환한다 (FR-16)`() {
        assertEquals(expected, nativePublishedAtToInstant(Timestamp.from(expected)))
    }

    @Test
    fun `네이티브 공개 시각이 없으면 빈 값을 유지한다 (FR-16)`() {
        assertEquals(null, nativePublishedAtToInstant(null))
    }
}
