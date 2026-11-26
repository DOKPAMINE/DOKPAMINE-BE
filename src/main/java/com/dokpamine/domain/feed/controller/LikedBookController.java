package com.dokpamine.domain.feed.controller;

import com.dokpamine.domain.feed.dto.response.LikedBookPage;
import com.dokpamine.domain.feed.service.LikedBookService;
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

@RestController
@RequestMapping("/api/feed/liked-books")
@Tag(name = "좋아요한 감상", description = "내가 좋아요한 공개 감상을 책별로 묶어 조회하는 API")
@SecurityRequirement(name = "BearerAuth")
public class LikedBookController {
    private final LikedBookService service;

    public LikedBookController(LikedBookService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "좋아요한 감상 책별 조회",
            description =
                    "내가 좋아요한 공개 감상을 ISBN 또는 제목·저자로 묶고, 묶음 안에서 작성자의 책별로 한 줄씩 반환합니다. 묶음은 마지막으로"
                            + " 좋아요한 시각 최신순이며 cursor로 다음 묶음을 이어 받습니다. 삭제·비공개 감상과 삭제된 책은"
                            + " 제외합니다(FR-09, NFR-09).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "책 묶음 목록과 다음 페이지 cursor. 없으면 items가 빈 배열"),
        @ApiResponse(
                responseCode = "400",
                description = "size가 1~50 밖이거나 cursor가 잘못됨",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content)
    })
    public LikedBookPage list(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "한 번에 받을 책 묶음 수(1~50). 마이 미리보기는 3", example = "20")
                    @RequestParam(defaultValue = "20")
                    int size,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략")
                    @RequestParam(required = false)
                    String cursor) {
        return service.list(AuthenticatedUser.id(auth), size, cursor);
    }
}
