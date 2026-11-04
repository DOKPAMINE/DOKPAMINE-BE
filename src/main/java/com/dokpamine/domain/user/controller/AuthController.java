package com.dokpamine.domain.user.controller;

import com.dokpamine.domain.user.dto.request.LoginRequest;
import com.dokpamine.domain.user.dto.request.SignupRequest;
import com.dokpamine.domain.user.dto.request.UpdateMeRequest;
import com.dokpamine.domain.user.dto.response.MeResponse;
import com.dokpamine.domain.user.dto.response.TokenResponse;
import com.dokpamine.domain.user.service.AuthService;
import com.dokpamine.global.jwt.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return service.signup(request);
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        return service.me(AuthenticatedUser.id(auth));
    }

    @PatchMapping("/me")
    public MeResponse updateMe(Authentication auth, @Valid @RequestBody UpdateMeRequest request) {
        return service.updateMe(AuthenticatedUser.id(auth), request);
    }
}
