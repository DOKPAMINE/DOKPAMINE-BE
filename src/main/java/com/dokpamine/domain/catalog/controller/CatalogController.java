package com.dokpamine.domain.catalog.controller;

import com.dokpamine.domain.catalog.dto.response.CatalogBook;
import com.dokpamine.domain.catalog.service.BookLookupService;
import com.dokpamine.domain.catalog.util.Genres;
import com.dokpamine.global.apiPayload.exception.ApiException;
import com.dokpamine.global.apiPayload.response.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "도서 조회", description = "ISBN 서지정보 조회 및 지원 장르 API")
@SecurityRequirement(name = "BearerAuth")
public class CatalogController {
    private final BookLookupService service;

    public CatalogController(BookLookupService service) {
        this.service = service;
    }

    @GetMapping("/isbn/{isbn}")
    @Operation(
            summary = "ISBN 도서 조회",
            description =
                    "ISBN을 13자리로 정규화하고 국립중앙도서관, 카카오, Google Books, Open Library 순서로 빈 정보를 보완합니다. 키가"
                            + " 없는 조회처는 건너뛰며 일부 응답 필드는 비어 있을 수 있습니다(FR-01).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회된 도서 정보와 첫 번째 유효 조회처"),
        @ApiResponse(
                responseCode = "400",
                description = "ISBN 형식 또는 체크섬 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "조회 가능한 도서 정보 없음",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CatalogBook byIsbn(
            @Parameter(
                            description = "ISBN-10 또는 ISBN-13. 하이픈과 공백을 제거한 뒤 체크섬을 검증합니다.",
                            example = "9788937460449")
                    @PathVariable
                    String isbn) {
        CatalogBook book = service.lookup(isbn);
        if (book == null) throw ApiException.notFound("도서 정보");
        return book;
    }

    @GetMapping("/genres")
    @Operation(summary = "지원 장르 목록 조회", description = "앱에서 선택하고 독서 통계에 사용하는 열두 장르를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "지원하는 장르 이름 목록"),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content)
    })
    public List<String> genres() {
        return Genres.ALL;
    }
}
