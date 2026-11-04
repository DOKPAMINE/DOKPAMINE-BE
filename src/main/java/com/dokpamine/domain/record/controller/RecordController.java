package com.dokpamine.domain.record.controller;

import com.dokpamine.domain.record.dto.BookDto;
import com.dokpamine.domain.record.dto.QuoteDto;
import com.dokpamine.domain.record.dto.ReviewDto;
import com.dokpamine.domain.record.dto.SessionDto;
import com.dokpamine.domain.record.dto.response.SnapshotResponse;
import com.dokpamine.domain.record.service.RecordService;
import com.dokpamine.global.apiPayload.exception.ApiException;
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

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "개인 기록", description = "책, 독서 세션, 감상 및 인용문 동기화 API")
@SecurityRequirement(name = "BearerAuth")
public class RecordController {
    private final RecordService service;

    public RecordController(RecordService service) {
        this.service = service;
    }

    @PutMapping("/books/{id}")
    @Operation(
            summary = "책 기록 동기화",
            description =
                    "클라이언트 UUID로 책을 생성하거나 갱신합니다. 같은 ID의 재전송은 한 건으로 유지하며, 삭제는 deleted=true로"
                            + " 전송합니다(NFR-05).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장된 책 기록"),
        @ApiResponse(
                responseCode = "400",
                description = "필드 검증 실패, 페이지 범위 오류 또는 경로와 본문 ID 불일치",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "다른 사용자의 책 ID",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BookDto putBook(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "본문 id와 같은 클라이언트 생성 책 UUID",
                            example = "0ad98f5b-71b1-43cd-9e5e-6654b6018f01")
                    @PathVariable
                    UUID id,
            @Valid @RequestBody BookDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertBook(AuthenticatedUser.id(auth), dto);
    }

    @PutMapping("/sessions/{id}")
    @Operation(
            summary = "독서 세션 동기화",
            description =
                    "책을 먼저 동기화한 뒤 세션을 저장합니다. 기존 세션의 시간을 수정하거나 MANUAL로 전송하면 수동 기록으로 유지하며 TIMER로 되돌릴"
                            + " 수 없습니다(T09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장된 세션과 서버가 결정한 source"),
        @ApiResponse(
                responseCode = "400",
                description = "필드 검증 실패, 시각·페이지 범위 오류 또는 ID 불일치",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "다른 사용자의 세션 또는 책",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "참조한 책이 아직 동기화되지 않음(BOOK_NOT_SYNCED)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SessionDto putSession(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "본문 id와 같은 클라이언트 생성 세션 UUID",
                            example = "777b648c-5b9a-47ec-a967-4fbb87ef3c02")
                    @PathVariable
                    UUID id,
            @Valid @RequestBody SessionDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertSession(AuthenticatedUser.id(auth), dto);
    }

    @PutMapping("/reviews/{id}")
    @Operation(
            summary = "감상 동기화",
            description =
                    "한줄평이나 독후감을 저장합니다. visibility 생략 시 PRIVATE이며, 삭제하지 않은 감상에는 두 내용 중 하나가"
                            + " 필요합니다(FR-08, NFR-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장된 감상"),
        @ApiResponse(
                responseCode = "400",
                description = "필드 검증 실패, 빈 감상 또는 ID 불일치",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "다른 사용자의 감상 또는 책",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "참조한 책이 아직 동기화되지 않음(BOOK_NOT_SYNCED)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ReviewDto putReview(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "본문 id와 같은 클라이언트 생성 감상 UUID",
                            example = "973e6cab-78b1-4eb9-b6c1-ef0d6bbc9803")
                    @PathVariable
                    UUID id,
            @Valid @RequestBody ReviewDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertReview(AuthenticatedUser.id(auth), dto);
    }

    @PutMapping("/quotes/{id}")
    @Operation(
            summary = "인용문 동기화",
            description = "책에서 수집한 문장과 페이지, 메모를 클라이언트 UUID 기준으로 생성하거나 갱신합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장된 인용문"),
        @ApiResponse(
                responseCode = "400",
                description = "필드 검증 실패 또는 경로와 본문 ID 불일치",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "다른 사용자의 인용문 또는 책",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "참조한 책이 아직 동기화되지 않음(BOOK_NOT_SYNCED)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public QuoteDto putQuote(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "본문 id와 같은 클라이언트 생성 인용문 UUID",
                            example = "63bdc7fb-cf49-4161-9f81-1d80286de904")
                    @PathVariable
                    UUID id,
            @Valid @RequestBody QuoteDto dto) {
        if (!id.equals(dto.id())) throw ApiException.badRequest("경로의 ID 와 본문의 ID 가 달라요.");
        return service.upsertQuote(AuthenticatedUser.id(auth), dto);
    }

    @GetMapping("/sync/snapshot")
    @Operation(
            summary = "내 기록 스냅샷 조회",
            description = "새 기기에서 복원할 수 있도록 삭제되지 않은 본인의 책, 세션, 감상, 인용문을 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제되지 않은 본인 기록 전체"),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content)
    })
    public SnapshotResponse snapshot(@Parameter(hidden = true) Authentication auth) {
        return service.snapshot(AuthenticatedUser.id(auth));
    }
}
