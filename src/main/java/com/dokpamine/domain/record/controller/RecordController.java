package com.dokpamine.domain.record.controller;

import com.dokpamine.domain.record.dto.BookDto;
import com.dokpamine.domain.record.dto.QuoteDto;
import com.dokpamine.domain.record.dto.ReviewDto;
import com.dokpamine.domain.record.dto.SessionDto;
import com.dokpamine.domain.record.dto.response.SnapshotResponse;
import com.dokpamine.domain.record.service.RecordService;
import com.dokpamine.global.apiPayload.exception.ApiException;
import com.dokpamine.global.jwt.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RecordController {
    private final RecordService service;

    public RecordController(RecordService service) {
        this.service = service;
    }

    @PutMapping("/books/{id}")
    public BookDto putBook(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody BookDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertBook(AuthenticatedUser.id(auth), dto);
    }

    @PutMapping("/sessions/{id}")
    public SessionDto putSession(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody SessionDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertSession(AuthenticatedUser.id(auth), dto);
    }

    @PutMapping("/reviews/{id}")
    public ReviewDto putReview(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody ReviewDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertReview(AuthenticatedUser.id(auth), dto);
    }

    @PutMapping("/quotes/{id}")
    public QuoteDto putQuote(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody QuoteDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertQuote(AuthenticatedUser.id(auth), dto);
    }

    @GetMapping("/sync/snapshot")
    public SnapshotResponse snapshot(Authentication auth) {
        return service.snapshot(AuthenticatedUser.id(auth));
    }
}
