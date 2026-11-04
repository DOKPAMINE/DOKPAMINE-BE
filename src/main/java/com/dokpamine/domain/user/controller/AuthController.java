package com.dokpamine.domain.user.controller;

import com.dokpamine.domain.user.dto.request.LoginRequest;
import com.dokpamine.domain.user.dto.request.SignupRequest;
import com.dokpamine.domain.user.dto.request.UpdateMeRequest;
import com.dokpamine.domain.user.dto.response.MeResponse;
import com.dokpamine.domain.user.dto.response.TokenResponse;
import com.dokpamine.domain.user.service.AuthService;
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

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "회원", description = "회원가입, 로그인 및 내 정보 관리 API")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "회원가입", description = "이메일로 가입하고 JWT와 사용자 정보를 반환합니다(FR-10).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "가입 완료 및 토큰 발급"),
        @ApiResponse(
                responseCode = "400",
                description = "가입 정보 형식 또는 길이 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이미 가입된 이메일(EMAIL_TAKEN)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return service.signup(request);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호를 확인하고 JWT와 사용자 정보를 반환합니다(FR-10).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공 및 토큰 발급"),
        @ApiResponse(
                responseCode = "400",
                description = "잘못된 로그인 요청 형식",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "이메일 또는 비밀번호 불일치(INVALID_CREDENTIALS)",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "인증한 사용자의 이메일, 닉네임 및 랭킹 참여 여부를 조회합니다.")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 정보 조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "사용자 정보 없음",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MeResponse me(@Parameter(hidden = true) Authentication auth) {
        return service.me(AuthenticatedUser.id(auth));
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정", description = "닉네임과 랭킹 참여 여부를 수정합니다. 생략하거나 null인 필드는 유지합니다.")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 정보 수정 성공"),
        @ApiResponse(
                responseCode = "400",
                description = "닉네임 길이 또는 요청 형식 오류",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "사용자 정보 없음",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MeResponse updateMe(
            @Parameter(hidden = true) Authentication auth,
            @Valid @RequestBody UpdateMeRequest request) {
        return service.updateMe(AuthenticatedUser.id(auth), request);
    }
}
