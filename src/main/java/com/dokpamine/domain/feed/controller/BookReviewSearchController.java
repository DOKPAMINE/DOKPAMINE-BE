package com.dokpamine.domain.feed.controller;

import com.dokpamine.domain.feed.dto.response.BookReviewGroup;
import com.dokpamine.domain.feed.service.BookReviewSearchService;
import com.dokpamine.global.apiPayload.response.ErrorResponse;
import com.dokpamine.global.jwt.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feed/books")
@Tag(name = "책별 감상 검색", description = "서버에 저장된 책 제목으로 공개 감상을 묶어 조회하는 API")
@SecurityRequirement(name = "BearerAuth")
public class BookReviewSearchController {
    private final BookReviewSearchService service;

    public BookReviewSearchController(BookReviewSearchService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "책별 공개 감상 검색",
            description =
                    "제목의 공백과 대소문자를 정규화해 검색하고 ISBN 또는 제목·저자로 묶습니다. 삭제·비공개 기록을 제외하며 최신 20개 묶음과 묶음별 감상"
                            + " 최대 100개를 반환합니다(FR-16, NFR-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "책별 공개 감상 묶음. 일치하는 결과가 없으면 빈 배열"),
        @ApiResponse(
                responseCode = "400",
                description = "검색어 누락 또는 공백만 입력함",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content)
    })
    public List<BookReviewGroup> search(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "검색할 책 제목의 일부. 공백만 입력할 수 없습니다.",
                            example = "데미안",
                            required = true)
                    @RequestParam(defaultValue = "")
                    String query) {
        return service.search(query, AuthenticatedUser.id(auth));
    }
}
