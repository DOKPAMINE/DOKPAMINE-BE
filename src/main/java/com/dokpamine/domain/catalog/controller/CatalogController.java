package com.dokpamine.domain.catalog.controller;

import com.dokpamine.domain.catalog.dto.response.CatalogBook;
import com.dokpamine.domain.catalog.service.BookLookupService;
import com.dokpamine.domain.catalog.util.Genres;
import com.dokpamine.global.apiPayload.exception.ApiException;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final BookLookupService service;

    public CatalogController(BookLookupService service) {
        this.service = service;
    }

    @GetMapping("/isbn/{isbn}")
    public CatalogBook byIsbn(@PathVariable String isbn) {
        CatalogBook book = service.lookup(isbn);
        if (book == null) throw ApiException.notFound("도서 정보");
        return book;
    }

    @GetMapping("/genres")
    public List<String> genres() {
        return Genres.ALL;
    }
}
