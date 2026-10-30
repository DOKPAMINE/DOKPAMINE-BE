package com.dokpamine.catalog

import com.dokpamine.common.ApiException
import com.dokpamine.config.CatalogProperties
import org.slf4j.LoggerFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

data class CatalogBook(
    val isbn: String,
    val title: String?,
    val author: String?,
    val publisher: String?,
    val totalPages: Int?,
    val genre: String?,
    val coverUrl: String?,
    val source: String,
)

/** 앱에서 선택할 수 있는 대표 장르. 통계는 이 값 기준으로 집계한다(FR-06). */
object Genres {
    val ALL = listOf("소설", "SF", "시/에세이", "인문", "역사", "사회/정치", "경제/경영", "자기계발", "과학", "예술", "만화", "기타")

    fun fromCategory(category: String?): String? {
        if (category.isNullOrBlank()) return null
        val c = category.lowercase()
        return when {
            "sf" in c || "science fiction" in c || "과학소설" in c -> "SF"
            "만화" in c || "comic" in c || "graphic novel" in c -> "만화"
            "에세이" in c || "시/" in c || "poetry" in c || "essay" in c -> "시/에세이"
            "소설" in c || "fiction" in c -> "소설"
            "역사" in c || "history" in c -> "역사"
            "경제" in c || "경영" in c || "business" in c || "economics" in c -> "경제/경영"
            "자기계발" in c || "self-help" in c -> "자기계발"
            "과학" in c || "science" in c -> "과학"
            "사회" in c || "정치" in c || "political" in c || "social" in c -> "사회/정치"
            "예술" in c || "art" in c || "music" in c -> "예술"
            "인문" in c || "철학" in c || "philosophy" in c || "psychology" in c -> "인문"
            else -> "기타"
        }
    }
}

@Service
class BookLookupService(
    private val properties: CatalogProperties,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val http = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout).followRedirects(HttpClient.Redirect.NORMAL).build(),
            ).apply { setReadTimeout(properties.timeout) },
        )
        .build()

    /** 알라딘 → Google Books → Open Library 순서로 조회하고(키 없는 소스는 건너뜀), 빈 칸은 뒤 결과로 채운다. */
    fun lookup(rawIsbn: String): CatalogBook? {
        val isbn = Isbn.normalize(rawIsbn) ?: throw ApiException.badRequest("올바른 ISBN 이 아니에요.")
        val results = listOfNotNull(
            if (properties.aladinTtbKey.isNotBlank()) safe("aladin") { aladin(isbn) } else null,
            if (properties.googleBooksKey.isNotBlank()) safe("google") { googleBooks(isbn) } else null,
            safe("openlibrary") { openLibrary(isbn) },
        )
        if (results.isEmpty()) return null
        return results.reduce { a, b ->
            a.copy(
                title = a.title ?: b.title,
                author = a.author ?: b.author,
                publisher = a.publisher ?: b.publisher,
                totalPages = a.totalPages ?: b.totalPages,
                genre = a.genre ?: b.genre,
                coverUrl = a.coverUrl ?: b.coverUrl,
            )
        }
    }

    private fun aladin(isbn: String): CatalogBook? {
        val body = http.get()
            .uri(
                "https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx?ttbkey={key}&itemIdType=ISBN13&ItemId={isbn}" +
                    "&output=js&Version=20131101&Cover=Big",
                properties.aladinTtbKey, isbn,
            )
            .retrieve()
            .body(String::class.java) ?: return null
        val item = jsonMapper.readTree(body).path("item").path(0)
        if (item.isMissingNode) return null
        return CatalogBook(
            isbn = isbn,
            title = item.text("title"),
            author = item.text("author")?.substringBefore(" (지은이)")?.substringBefore(",")?.trim(),
            publisher = item.text("publisher"),
            totalPages = item.path("subInfo").path("itemPage").asInt(0).takeIf { it > 0 },
            genre = Genres.fromCategory(item.text("categoryName")),
            coverUrl = item.text("cover"),
            source = "aladin",
        )
    }

    private fun googleBooks(isbn: String): CatalogBook? {
        val body = http.get()
            .uri("https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}&key={key}", isbn, properties.googleBooksKey)
            .retrieve()
            .body(String::class.java) ?: return null
        val info = jsonMapper.readTree(body).path("items").path(0).path("volumeInfo")
        if (info.isMissingNode) return null
        return CatalogBook(
            isbn = isbn,
            title = listOfNotNull(info.text("title"), info.text("subtitle")).joinToString(": ").ifBlank { null },
            author = info.path("authors").path(0).let { if (it.isMissingNode) null else it.asString() },
            publisher = info.text("publisher"),
            totalPages = info.path("pageCount").asInt(0).takeIf { it > 0 },
            genre = Genres.fromCategory(info.path("categories").path(0).let { if (it.isMissingNode) null else it.asString() }),
            coverUrl = info.path("imageLinks").text("thumbnail")?.replace("http://", "https://"),
            source = "google",
        )
    }

    private fun openLibrary(isbn: String): CatalogBook? {
        val body = http.get()
            .uri("https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data", isbn)
            .retrieve()
            .body(String::class.java) ?: return null
        val book = jsonMapper.readTree(body).path("ISBN:$isbn")
        if (book.isMissingNode) return null
        return CatalogBook(
            isbn = isbn,
            title = book.text("title"),
            author = book.path("authors").path(0).text("name"),
            publisher = book.path("publishers").path(0).text("name"),
            totalPages = book.path("number_of_pages").asInt(0).takeIf { it > 0 },
            genre = book.path("subjects").firstNotNullOfOrNull { Genres.fromCategory(it.text("name")).takeIf { g -> g != "기타" } },
            coverUrl = book.path("cover").text("medium")?.replace("http://", "https://"),
            source = "openlibrary",
        )
    }

    private fun <T> safe(name: String, block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        log.warn("{} 도서 조회 실패: {}", name, e.message)
        null
    }

    private fun JsonNode.text(field: String): String? =
        path(field).takeUnless { it.isMissingNode || it.isNull }?.asString()?.takeIf { it.isNotBlank() }
}

object Isbn {
    /** 하이픈을 제거하고 체크섬을 검증한 뒤 ISBN-13 으로 돌려준다. */
    fun normalize(raw: String): String? {
        val s = raw.replace("-", "").replace(" ", "").uppercase()
        return when {
            s.length == 13 && s.all(Char::isDigit) && isbn13Valid(s) -> s
            s.length == 10 && s.substring(0, 9).all(Char::isDigit) && (s[9].isDigit() || s[9] == 'X') && isbn10Valid(s) ->
                "978${s.substring(0, 9)}".let { it + isbn13Check(it) }
            else -> null
        }
    }

    private fun isbn13Check(first12: String): Int =
        (10 - first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum() % 10) % 10

    private fun isbn13Valid(s: String) = isbn13Check(s.substring(0, 12)) == s[12] - '0'

    private fun isbn10Valid(s: String): Boolean =
        s.mapIndexed { i, c -> (10 - i) * if (c == 'X') 10 else c - '0' }.sum() % 11 == 0
}

@RestController
@RequestMapping("/api/catalog")
class CatalogController(private val lookupService: BookLookupService) {

    @GetMapping("/isbn/{isbn}")
    fun byIsbn(@PathVariable isbn: String): CatalogBook =
        lookupService.lookup(isbn) ?: throw ApiException.notFound("도서 정보")

    @GetMapping("/genres")
    fun genres(): List<String> = Genres.ALL
}
