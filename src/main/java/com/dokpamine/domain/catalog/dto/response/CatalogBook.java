package com.dokpamine.domain.catalog.dto.response;

public record CatalogBook(
        String isbn,
        String title,
        String author,
        String publisher,
        Integer totalPages,
        String genre,
        String coverUrl,
        String sideCoverUrl,
        String yes24ProductUrl,
        String source) {}
