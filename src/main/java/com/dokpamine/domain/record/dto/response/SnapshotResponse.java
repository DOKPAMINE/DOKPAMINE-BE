package com.dokpamine.domain.record.dto.response;

import com.dokpamine.domain.record.dto.BookDto;
import com.dokpamine.domain.record.dto.QuoteDto;
import com.dokpamine.domain.record.dto.ReviewDto;
import com.dokpamine.domain.record.dto.SessionDto;

import jakarta.validation.constraints.*;

import java.util.List;

public record SnapshotResponse(
        List<BookDto> books,
        List<SessionDto> sessions,
        List<ReviewDto> reviews,
        List<QuoteDto> quotes) {}
