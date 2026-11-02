package com.dokpamine.catalog

import com.dokpamine.common.ApiException
import com.dokpamine.config.CatalogProperties
import org.slf4j.LoggerFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
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

    fun fromKdc(kdc: String?): String? {
        val value = kdc?.trim()?.takeIf { Regex("[0-9]{3}(\\.[0-9]+)?").matches(it) } ?: return null
        val code = value.take(3).toInt()
        return when {
            // 국립중앙도서관 연구 p.270: 199 도덕훈·교훈의 핵심 주제는 인생훈이다.
            // https://oak.go.kr/nl-ir/bitstream/2020.oak/388/1/국립중앙도서관%20소장자료%20디지털화%20중장기%20전략%20연구.pdf
            code == 199 -> "자기계발"
            code == 657 -> "만화"
            code in 320..329 -> "경제/경영"
            code in 800..899 -> when (code % 10) {
                3 -> "소설" // KDC만으로 SF 여부는 알 수 없다.
                1, 4 -> "시/에세이"
                // 문학의 나머지 형식(희곡·평론 등)을 소설로 오인하지 않도록 인문으로 묶는다.
                else -> "인문"
            }
            code in 900..999 -> "역사"
            code in 300..399 -> "사회/정치"
            code in 100..299 || code in 700..799 -> "인문"
            code in 400..599 -> "과학"
            code in 600..699 -> "예술"
            else -> "기타"
        }
    }

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

fun interface CatalogFetcher {
    fun fetch(source: String, isbn: String): String?
}

@Component
class HttpCatalogFetcher(private val properties: CatalogProperties) : CatalogFetcher {
    private val http = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout).followRedirects(HttpClient.Redirect.NORMAL).build(),
            ).apply { setReadTimeout(properties.timeout) },
        )
        .build()

    override fun fetch(source: String, isbn: String): String? = when (source) {
        "nlk" -> http.get().uri(
            "https://www.nl.go.kr/seoji/SearchApi.do?cert_key={key}&result_style=json&page_no=1&page_size=1&isbn={isbn}",
            properties.nlkCertKey, isbn,
        )
        "kakao" -> http.get()
            .uri("https://dapi.kakao.com/v3/search/book?target=isbn&query={isbn}", isbn)
            .header("Authorization", "KakaoAK ${properties.kakaoRestApiKey}")
        "google" -> http.get().uri(
            "https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}&key={key}", isbn, properties.googleBooksKey,
        )
        "openlibrary" -> http.get()
            .uri("https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data", isbn)
        else -> error("지원하지 않는 도서 조회처입니다.")
    }.retrieve().body(String::class.java)
}

@Service
class BookLookupService(
    private val properties: CatalogProperties,
    private val jsonMapper: JsonMapper,
    private val fetcher: CatalogFetcher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 국립중앙도서관 → 카카오 → Google Books → Open Library 순서로 빈 칸을 채운다(키 없는 조회처는 건너뜀). */
    fun lookup(rawIsbn: String): CatalogBook? {
        val isbn = Isbn.normalize(rawIsbn) ?: throw ApiException.badRequest("올바른 ISBN 이 아니에요.")
        val results = listOfNotNull(
            if (properties.nlkCertKey.isNotBlank()) safe("nlk") { nlk(isbn) } else null,
            if (properties.kakaoRestApiKey.isNotBlank()) safe("kakao") { kakao(isbn) } else null,
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

    private fun nlk(isbn: String): CatalogBook? {
        // FR-01: 공식 ISBN 서지정보 안내의 문자열 필드(PAGE·KDC·TITLE_URL)를 사용한다.
        // https://www.nl.go.kr/NL/contents/N31101030500.do
        val body = fetcher.fetch("nlk", isbn) ?: return null
        val item = jsonMapper.readTree(body).path("docs").firstOrNull {
            it.text("EA_ISBN") == isbn
        } ?: return null
        return CatalogBook(
            isbn = isbn,
            title = item.text("TITLE"),
            author = item.text("AUTHOR")?.substringBefore(';')?.substringAfter(':')?.substringBefore(',')
                ?.trim()?.takeIf { it.isNotBlank() },
            publisher = item.text("PUBLISHER"),
            totalPages = item.text("PAGE")?.let { Regex("-?[0-9]+").findAll(it).lastOrNull()?.value?.toIntOrNull() }
                ?.takeIf { it > 0 },
            genre = Genres.fromKdc(item.text("KDC")),
            coverUrl = item.text("TITLE_URL")?.replace("http://", "https://"),
            source = "nlk",
        )
    }

    private fun kakao(isbn: String): CatalogBook? {
        // FR-01: ISBN10·ISBN13이 공백으로 구분되므로 요청 ISBN13과 정확히 일치하는 문서만 채택한다.
        // https://developers.kakao.com/docs/latest/ko/daum-search/dev-guide#search-book
        val body = fetcher.fetch("kakao", isbn) ?: return null
        val item = jsonMapper.readTree(body).path("documents").firstOrNull {
            isbn in (it.text("isbn")?.split(Regex("\\s+")) ?: emptyList())
        } ?: return null
        return CatalogBook(
            isbn = isbn,
            title = item.text("title"),
            author = item.path("authors").path(0).takeUnless { it.isMissingNode || it.isNull }
                ?.asString()?.takeIf { it.isNotBlank() },
            publisher = item.text("publisher"),
            totalPages = null,
            genre = null,
            coverUrl = item.text("thumbnail")?.replace("http://", "https://"),
            source = "kakao",
        )
    }

    private fun googleBooks(isbn: String): CatalogBook? {
        val body = fetcher.fetch("google", isbn) ?: return null
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
        val body = fetcher.fetch("openlibrary", isbn) ?: return null
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
        // 예외 메시지에는 인증키가 들어 있는 요청 URL이 포함될 수 있다.
        log.warn("{} 도서 조회 실패: {}", name, e.javaClass.simpleName)
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
