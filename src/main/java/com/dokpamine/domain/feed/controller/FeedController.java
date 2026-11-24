package com.dokpamine.domain.feed.controller;

import com.dokpamine.domain.feed.dto.response.FeedItem;
import com.dokpamine.domain.feed.dto.response.LikeResponse;
import com.dokpamine.domain.feed.service.FeedService;
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

import java.util.*;

@RestController
@RequestMapping("/api/feed")
@Tag(name = "감상 피드", description = "공개 감상 피드, 상세 조회 및 좋아요 API")
@SecurityRequirement(name = "BearerAuth")
public class FeedController {
    private final FeedService service;

    public FeedController(FeedService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "공개 감상 피드 조회",
            description =
                    "사람의 책 하나를 박스 하나로 묶어 조회합니다. 공개 독후감이 있는 책만 포함하며, 박스마다 가장 최근에 새로 쓴 공개 독후감 한 건을 독후감을 쓴 시각 최신순으로 최대 100개 반환합니다(FR-08).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "박스별 대표 독후감 목록. 없으면 빈 배열"),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content)
    })
    public List<FeedItem> list(@Parameter(hidden = true) Authentication auth) {
        return service.list(AuthenticatedUser.id(auth));
    }

    @GetMapping("/user-books/{id}")
    @Operation(
            summary = "책 박스의 공개 감상 조회",
            description =
                    "한 사람이 한 책에 쓴 공개 감상(한줄평·독후감)을 모두 조회합니다. 공개 감상이 없거나 책이 삭제됐으면 404로 처리합니다(FR-08, NFR-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "공개 감상 목록(공개 시각 최신순)"),
        @ApiResponse(responseCode = "400", description = "잘못된 책 UUID", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "책 없음, 삭제됨 또는 공개 감상 없음",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<FeedItem> bookReviews(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "조회할 사람의 책(user_books) UUID. 피드 항목의 userBookId",
                            example = "5b1f0c3e-2a7d-4c1b-9f43-8d2e6a7b9c10")
                    @PathVariable
                    UUID id) {
        return service.bookReviews(AuthenticatedUser.id(auth), id);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "감상 상세 조회",
            description =
                    "공개 감상 또는 본인이 작성한 비공개 감상의 공개 응답 필드를 조회합니다. 다른 사용자의 비공개 감상은 404로 처리합니다(NFR-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "감상 상세와 좋아요 상태"),
        @ApiResponse(responseCode = "400", description = "잘못된 감상 UUID", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "감상 없음, 삭제됨 또는 비공개 접근 제한",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public FeedItem detail(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "조회할 감상의 UUID",
                            example = "973e6cab-78b1-4eb9-b6c1-ef0d6bbc9803")
                    @PathVariable
                    UUID id) {
        return service.detail(AuthenticatedUser.id(auth), id);
    }

    @PostMapping("/{id}/like")
    @Operation(
            summary = "감상 좋아요",
            description = "접근 가능한 감상에 좋아요를 남깁니다. 같은 사용자의 반복 요청은 좋아요 한 건으로 유지합니다(FR-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "좋아요 수와 likedByMe=true"),
        @ApiResponse(responseCode = "400", description = "잘못된 감상 UUID", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "감상 없음, 삭제됨 또는 비공개 접근 제한",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LikeResponse like(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "좋아요를 남길 감상의 UUID",
                            example = "973e6cab-78b1-4eb9-b6c1-ef0d6bbc9803")
                    @PathVariable
                    UUID id) {
        return service.like(AuthenticatedUser.id(auth), id);
    }

    @DeleteMapping("/{id}/like")
    @Operation(
            summary = "감상 좋아요 취소",
            description = "접근 가능한 감상에서 본인의 좋아요를 취소합니다. 좋아요가 없어도 취소 결과를 반환합니다(FR-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "좋아요 수와 likedByMe=false"),
        @ApiResponse(responseCode = "400", description = "잘못된 감상 UUID", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "감상 없음, 삭제됨 또는 비공개 접근 제한",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LikeResponse unlike(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description = "좋아요를 취소할 감상의 UUID",
                            example = "973e6cab-78b1-4eb9-b6c1-ef0d6bbc9803")
                    @PathVariable
                    UUID id) {
        return service.unlike(AuthenticatedUser.id(auth), id);
    }
}
