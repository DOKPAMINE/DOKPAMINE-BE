package com.dokpamine.domain.ranking.controller;

import com.dokpamine.domain.ranking.dto.response.RankingResponse;
import com.dokpamine.domain.ranking.service.RankingService;
import com.dokpamine.global.jwt.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ranking")
@Tag(name = "독서왕 랭킹", description = "참여 동의한 사용자의 주간 타이머 독서 시간 랭킹 API")
@SecurityRequirement(name = "BearerAuth")
public class RankingController {
    private final RankingService service;

    public RankingController(RankingService service) {
        this.service = service;
    }

    @GetMapping("/weekly")
    @Operation(
            summary = "주간 독서왕 랭킹 조회",
            description =
                    "KST 월요일 기준으로 TIMER 세션을 하루 최대 180분까지 집계합니다. 상위 30명과, 본인이 31위 이하이면 본인 포함 주변 30명을"
                            + " 반환합니다(FR-15).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주 시작일, 참여 여부, 상위 랭킹과 본인 주변 랭킹"),
        @ApiResponse(responseCode = "400", description = "잘못된 날짜 형식", content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content)
    })
    public RankingResponse weekly(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(
                            description =
                                    "조회할 주에 속한 날짜(YYYY-MM-DD). 해당 주 월요일로 정규화하며 생략 시 KST 이번 주를"
                                            + " 조회합니다.",
                            example = "2026-10-26")
                    @RequestParam(required = false)
                    LocalDate weekStart) {
        return service.weekly(AuthenticatedUser.id(auth), weekStart);
    }
}
