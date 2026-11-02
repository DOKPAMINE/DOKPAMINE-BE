package com.dokpamine.catalog

import com.dokpamine.common.ApiException
import com.dokpamine.config.CatalogProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import tools.jackson.databind.json.JsonMapper
import java.net.SocketTimeoutException

@ExtendWith(OutputCaptureExtension::class)
class BookLookupServiceTest {
    private val isbn = "9788937460449"
    private val json = JsonMapper.builder().build()
    private val properties = CatalogProperties(nlkCertKey = "test-nlk", kakaoRestApiKey = "test-kakao", googleBooksKey = "test-google")

    @ParameterizedTest
    @ValueSource(strings = ["312 p.", "312p", "xii, 312 p.", "12, 312 p."])
    fun `FR-01 국립중앙도서관 문자열에서 본문 쪽수와 첫 저자와 장르를 읽는다`(pages: String) {
        val fetcher = FakeFetcher(mapOf("nlk" to nlk(pages = pages)))
        val book = service(fetcher).lookup(isbn)!!

        assertEquals(isbn, book.isbn)
        assertEquals("국내 도서", book.title)
        assertEquals("홍길동", book.author)
        assertEquals("출판사", book.publisher)
        assertEquals(312, book.totalPages)
        assertEquals("소설", book.genre)
        assertNull(book.coverUrl)
        assertEquals("nlk", book.source)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "xii p.", "0 p.", "-1 p.", "999999999999 p."])
    fun `FR-01 유효한 양수가 없는 쪽수는 비워 둔다`(pages: String) {
        assertNull(service(FakeFetcher(mapOf("nlk" to nlk(pages = pages)))).lookup(isbn)!!.totalPages)
    }

    @Test
    fun `FR-01 국립중앙도서관 표지의 HTTP 주소를 HTTPS로 바꾼다`() {
        val book = service(FakeFetcher(mapOf("nlk" to nlk(cover = "http://example.com/nlk.jpg")))).lookup(isbn)!!
        assertEquals("https://example.com/nlk.jpg", book.coverUrl)
    }

    @Test
    fun `FR-01 카카오 예시에서 일치하는 ISBN의 첫 저자와 HTTPS 표지를 읽는다`() {
        val body = """{"documents":[
            {"isbn":"9788996991342","title":"다른 책","thumbnail":"http://example.com/wrong.jpg"},
            {"isbn":"8937460440 9788937460449","title":"카카오 도서","authors":["홍길동","김철수"],
             "publisher":"카카오 출판사","thumbnail":"http://example.com/cover.jpg"}
        ]}"""
        val book = service(FakeFetcher(mapOf("kakao" to body))).lookup(isbn)!!

        assertEquals("카카오 도서", book.title)
        assertEquals("홍길동", book.author)
        assertEquals("카카오 출판사", book.publisher)
        assertEquals("https://example.com/cover.jpg", book.coverUrl)
        assertNull(book.totalPages)
        assertNull(book.genre)
        assertEquals("kakao", book.source)
    }

    @ParameterizedTest
    @ValueSource(strings = ["9788996991342", "8996991341", "97889374604490", ""])
    fun `FR-01 카카오 응답에 요청 ISBN13이 없으면 채택하지 않는다`(responseIsbn: String) {
        val body = json.writeValueAsString(mapOf("documents" to listOf(mapOf("isbn" to responseIsbn, "title" to "다른 책"))))
        assertNull(service(FakeFetcher(mapOf("kakao" to body))).lookup(isbn))
    }

    @ParameterizedTest
    @ValueSource(strings = ["8937460440", " 9788937460449", "8937460440 ", "89-374-6044-0"])
    fun `FR-01 카카오 ISBN이 한쪽만 있어도 같은 책이면 채택한다`(responseIsbn: String) {
        val body = json.writeValueAsString(mapOf("documents" to listOf(mapOf("isbn" to responseIsbn, "title" to "카카오 도서"))))
        assertEquals("카카오 도서", service(FakeFetcher(mapOf("kakao" to body))).lookup(isbn)?.title)
    }

    @Test
    fun `FR-01 국립중앙도서관 EA_ISBN은 하이픈을 빼고 요청 ISBN과 비교한다`() {
        val body = nlk().replace("\"$isbn\"", "\"978-89-374-6044-9\"")
        assertEquals("국내 도서", service(FakeFetcher(mapOf("nlk" to body))).lookup(isbn)?.title)
        val other = nlk().replace("\"$isbn\"", "\"9788996991342\"")
        assertNull(service(FakeFetcher(mapOf("nlk" to other))).lookup(isbn))
    }

    @Test
    fun `FR-01 카카오의 비어 있는 선택 필드는 null로 둔다`() {
        val body = """{"documents":[{"isbn":"$isbn","title":" ","authors":[],"publisher":null,"thumbnail":""}]}"""
        assertEquals(CatalogBook(isbn, null, null, null, null, null, null, "kakao"), service(FakeFetcher(mapOf("kakao" to body))).lookup(isbn))
    }

    @Test
    fun `FR-01 국립중앙도서관 쪽수와 카카오 표지를 합치고 첫 출처와 기존 값을 유지한다`() {
        val fetcher = FakeFetcher(mapOf("nlk" to nlk(), "kakao" to kakao(), "google" to google(), "openlibrary" to openLibrary()))
        val book = service(fetcher).lookup("8937460440")!!

        assertEquals(312, book.totalPages)
        assertEquals("https://example.com/kakao.jpg", book.coverUrl)
        assertEquals("국내 도서", book.title)
        assertEquals("소설", book.genre)
        assertEquals("nlk", book.source)
        assertEquals(listOf("nlk", "kakao", "google", "openlibrary"), fetcher.calls.map { it.first })
        assertEquals(listOf(isbn), fetcher.calls.map { it.second }.distinct())
    }

    @Test
    fun `FR-01 앞 조회처의 빈 필드는 Google Books와 Open Library로 보완한다`() {
        val fetcher = FakeFetcher(mapOf("nlk" to nlk(pages = "", kdc = "잘못된 분류"), "kakao" to kakao(), "google" to google()))
        val book = service(fetcher).lookup(isbn)!!
        assertEquals(400, book.totalPages)
        assertEquals("과학", book.genre)
        assertEquals("nlk", book.source)

        val lastBook = service(FakeFetcher(mapOf("openlibrary" to openLibrary()))).lookup(isbn)!!
        assertEquals("Open Library 도서", lastBook.title)
        assertEquals("저자", lastBook.author)
        assertEquals("출판사", lastBook.publisher)
        assertEquals(500, lastBook.totalPages)
        assertEquals("역사", lastBook.genre)
        assertEquals("https://example.com/openlibrary.jpg", lastBook.coverUrl)
        assertEquals("openlibrary", lastBook.source)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  "])
    fun `FR-01 모든 키가 비어 있으면 Open Library만 호출한다`(key: String) {
        val fetcher = FakeFetcher()
        assertNull(service(fetcher, CatalogProperties(nlkCertKey = key, kakaoRestApiKey = key, googleBooksKey = key)).lookup(isbn))
        assertEquals(listOf("openlibrary" to isbn), fetcher.calls)
    }

    @ParameterizedTest
    @ValueSource(strings = ["nlk", "kakao", "google"])
    fun `FR-01 키가 없는 조회처만 건너뛰고 나머지 순서는 유지한다`(source: String) {
        val configured = properties.copy(
            nlkCertKey = if (source == "nlk") "" else properties.nlkCertKey,
            kakaoRestApiKey = if (source == "kakao") "" else properties.kakaoRestApiKey,
            googleBooksKey = if (source == "google") "" else properties.googleBooksKey,
        )
        val fetcher = FakeFetcher()
        service(fetcher, configured).lookup(isbn)
        assertEquals(listOf("nlk", "kakao", "google", "openlibrary").filter { it != source }, fetcher.calls.map { it.first })
    }

    @Test
    fun `FR-01 외부 조회의 타임아웃과 HTTP 오류는 키를 로그에 남기지 않고 넘어간다`(output: CapturedOutput) {
        val errors = listOf(
            SocketTimeoutException("cert_key=test-secret"),
            HttpClientErrorException(HttpStatus.UNAUTHORIZED, "test-secret"),
            HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "test-secret"),
        )
        val calls = mutableListOf<String>()
        val book = service(CatalogFetcher { source, _ ->
            calls += source
            if (source == "openlibrary") openLibrary() else throw errors[calls.lastIndex]
        }).lookup(isbn)!!
        assertEquals("openlibrary", book.source)
        assertEquals(listOf("nlk", "kakao", "google", "openlibrary"), calls)
        assertFalse(output.all.contains("test-secret"))
    }

    @Test
    fun `FR-01 파싱 오류와 빈 응답은 다음 조회처로 넘어간다`() {
        val fetcher = FakeFetcher(mapOf("nlk" to "not-json", "kakao" to "not-json", "google" to google()))
        val book = service(fetcher).lookup(isbn)!!
        assertEquals("Google 도서: 부제", book.title)
        assertEquals("Google 저자", book.author)
        assertEquals("Google 출판사", book.publisher)
        assertEquals("https://example.com/google.jpg", book.coverUrl)
        assertEquals("google", book.source)
        assertNull(service(FakeFetcher(mapOf("nlk" to "{\"docs\":[]}", "kakao" to "{\"documents\":[]}"))).lookup(isbn))
    }

    @Test
    fun `FR-01 잘못된 ISBN은 외부 조회 없이 거절한다`() {
        val fetcher = FakeFetcher()
        assertThrows(ApiException::class.java) { service(fetcher).lookup("invalid") }
        assertEquals(emptyList<Pair<String, String>>(), fetcher.calls)
    }

    private fun service(fetcher: CatalogFetcher, configured: CatalogProperties = properties) = BookLookupService(configured, json, fetcher)

    private fun nlk(pages: String = "312 p.", kdc: String = "813.7", cover: String = "") = json.writeValueAsString(
        mapOf("TOTAL_COUNT" to "1", "docs" to listOf(mapOf(
            "EA_ISBN" to isbn, "TITLE" to "국내 도서", "AUTHOR" to "지은이: 홍길동, 김철수 ; 옮긴이: 번역자",
            "PUBLISHER" to "출판사", "PAGE" to pages, "KDC" to kdc, "TITLE_URL" to cover,
        ))),
    )

    private fun kakao() = """{"documents":[{"isbn":"8937460440 $isbn","title":"카카오 도서","thumbnail":"http://example.com/kakao.jpg"}]}"""
    private fun google() = """{"items":[{"volumeInfo":{"title":"Google 도서","subtitle":"부제","authors":["Google 저자"],
        "publisher":"Google 출판사","pageCount":400,"categories":["Science"],"imageLinks":{"thumbnail":"http://example.com/google.jpg"}}}]}"""
    private fun openLibrary() = """{"ISBN:$isbn":{"title":"Open Library 도서","authors":[{"name":"저자"}],"publishers":[{"name":"출판사"}],
        "number_of_pages":500,"subjects":[{"name":"History"}],"cover":{"medium":"http://example.com/openlibrary.jpg"}}}"""

    private class FakeFetcher(private val responses: Map<String, String> = emptyMap()) : CatalogFetcher {
        val calls = mutableListOf<Pair<String, String>>()
        override fun fetch(source: String, isbn: String): String? {
            calls += source to isbn
            return responses[source]
        }
    }
}
