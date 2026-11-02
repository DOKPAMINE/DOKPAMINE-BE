package com.dokpamine.feed

import com.dokpamine.config.userId
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class BookReviewGroup(
    val bookKey: String,
    val bookTitle: String,
    val bookAuthor: String,
    val isbn: String?,
    val reviews: List<FeedItem>,
)

@RestController
@RequestMapping("/api/feed/books")
class BookReviewSearchController(private val search: BookReviewSearchService) {

    @GetMapping
    fun search(auth: Authentication, @RequestParam(defaultValue = "") query: String): List<BookReviewGroup> =
        search.search(query, auth.userId())
}
