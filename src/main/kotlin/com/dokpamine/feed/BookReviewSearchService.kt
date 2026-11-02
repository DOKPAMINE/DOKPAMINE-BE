package com.dokpamine.feed

import com.dokpamine.catalog.Isbn
import com.dokpamine.common.ApiException
import com.dokpamine.record.Visibility
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class BookReviewSearchService(
    private val searchBooks: BookReviewSearchRepository,
    private val items: FeedItemMapper,
) {
    @Transactional(readOnly = true)
    fun search(query: String, me: UUID): List<BookReviewGroup> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) throw ApiException.badRequest("검색어를 입력해 주세요.")

        // LIKE는 공백 사이를 넓게 찾고 정규화한 제목으로 재검증해 기존 검색 의미를 유지한다.
        val pattern = normalizedQuery.split(' ').joinToString("%", "%", "%") { token ->
            token.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        }
        val matchingKeys = searchBooks.findTitleCandidates(pattern, Visibility.PUBLIC, PageRequest.of(0, TITLE_CANDIDATE_LIMIT))
            .filter { normalize(it.title).contains(normalizedQuery) }
            .map { bookKey(it.isbn, it.title, it.author) }.toSet()
        if (matchingKeys.isEmpty()) return emptyList()

        val matchingIsbns = matchingKeys.filter { it.startsWith("isbn:") }.map { it.removePrefix("isbn:") }
        val matches = searchBooks.findPublicBookSummaries(pattern, matchingIsbns, matchingIsbns.flatMap(::isbnSearchForms))
            .filter { bookKey(it.isbn, it.title, it.author) in matchingKeys }
            .groupBy { bookKey(it.isbn, it.title, it.author) }
            .map { (key, entries) -> key to entries.sortedWith(latestFirst) }
            .sortedWith { left, right -> latestFirst.compare(left.second.first(), right.second.first()) }
            .take(20)
            .associate { (key, entries) ->
                key to searchBooks.findPublicReviews(entries.map { UUID.fromString(it.id) }, Visibility.PUBLIC, PageRequest.of(0, 100))
            }
        val selected = matches.values.flatten()
        if (selected.isEmpty()) return emptyList()
        val mapped = items.toItems(selected, me).associateBy { it.id }

        return matches.mapNotNull { (key, entries) ->
            val items = entries.mapNotNull { mapped[it.id] }
            val latest = items.firstOrNull() ?: return@mapNotNull null
            BookReviewGroup(key, latest.bookTitle, latest.bookAuthor, key.takeIf { it.startsWith("isbn:") }?.removePrefix("isbn:"), items)
        }
    }

    private fun bookKey(rawIsbn: String?, title: String, author: String): String {
        isbn(rawIsbn)?.let { return "isbn:$it" }
        val titleAndAuthor = "${normalize(title)}\n${normalize(author)}"
        return "text:" + Base64.getUrlEncoder().withoutPadding().encodeToString(titleAndAuthor.toByteArray(Charsets.UTF_8))
    }

    private fun isbn(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { Isbn.normalize(it) ?: it }

    // FR-16: 제목이 다른 ISBN-10 사본도 SQL에서 좁혀 읽되 최종 묶음은 기존 정규화로 검증한다.
    private fun isbnSearchForms(value: String): List<String> {
        if (Isbn.normalize(value) != value) return emptyList()
        if (!value.startsWith("978")) return listOf(value)
        val firstNine = value.substring(3, 12)
        val check = (11 - firstNine.mapIndexed { index, digit -> (10 - index) * (digit - '0') }.sum() % 11) % 11
        return listOf(value, firstNine + if (check == 10) "X" else check.toString())
    }

    private fun normalize(value: String): String = whitespace.replace(value, " ").trim().lowercase(Locale.ROOT)

    private companion object {
        const val TITLE_CANDIDATE_LIMIT = 500
        val whitespace = Regex("[\\s\\p{Z}]+")
        val latestFirst = compareByDescending<PublicBookSummary> { nativePublishedAtToInstant(it.latestPublishedAt) ?: Instant.MIN }
            .thenBy { it.latestReviewId }
    }
}
