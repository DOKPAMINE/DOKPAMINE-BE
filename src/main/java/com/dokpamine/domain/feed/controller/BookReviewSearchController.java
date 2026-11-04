package com.dokpamine.domain.feed.controller;

import com.dokpamine.domain.feed.dto.response.BookReviewGroup;
import com.dokpamine.domain.feed.service.BookReviewSearchService;
import com.dokpamine.global.jwt.AuthenticatedUser;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feed/books")
public class BookReviewSearchController {
    private final BookReviewSearchService service;

    public BookReviewSearchController(BookReviewSearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<BookReviewGroup> search(
            Authentication auth, @RequestParam(defaultValue = "") String query) {
        return service.search(query, AuthenticatedUser.id(auth));
    }
}
