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
        val matchingKeys = searchBooks.findTitleCandidates(pattern, Visibility.PUBLIC)
            .filter { normalize(it.title).contains(normalizedQuery) }
            .map { bookKey(it.isbn, it.title, it.author) }.toSet()
        if (matchingKeys.isEmpty()) return emptyList()

        val matches = searchBooks.findPublicBookSummaries()
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

    private fun normalize(value: String): String = whitespace.replace(value, " ").trim().lowercase(Locale.ROOT)

    private companion object {
        val whitespace = Regex("[\\s\\p{Z}]+")
        val latestFirst = compareByDescending<PublicBookSummary> { nativePublishedAtToInstant(it.latestPublishedAt) ?: Instant.MIN }
            .thenBy { it.latestReviewId }
    }
}
