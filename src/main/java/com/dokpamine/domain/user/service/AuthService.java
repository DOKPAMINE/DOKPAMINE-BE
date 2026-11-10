package com.dokpamine.domain.user.service;

import com.dokpamine.domain.user.dto.request.LoginRequest;
import com.dokpamine.domain.user.dto.request.SignupRequest;
import com.dokpamine.domain.user.dto.request.UpdateMeRequest;
import com.dokpamine.domain.user.dto.response.MeResponse;
import com.dokpamine.domain.user.dto.response.TokenResponse;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.domain.user.repository.UserRepository;
import com.dokpamine.global.apiPayload.exception.ApiException;
import com.dokpamine.global.config.JwtProperties;
import com.dokpamine.global.util.TextValues;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwords,
            JwtEncoder encoder,
            JwtProperties properties) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = TextValues.trim(request.email()).toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email))
            throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일이에요.");
        User user =
                users.save(
                        new User(
                                UUID.randomUUID(),
                                email,
                                passwords.encode(request.password()),
                                TextValues.trim(request.nickname())));
        return issueToken(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(TextValues.trim(request.email()).toLowerCase(Locale.ROOT));
        if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않아요.");
        }
        return issueToken(user);
    }

    public MeResponse me(UUID id) {
        return new MeResponse(findUser(id));
    }

    @Transactional
    public MeResponse updateMe(UUID id, UpdateMeRequest request) {
        User user = findUser(id);
        if (request.nickname() != null) user.setNickname(TextValues.trim(request.nickname()));
        if (request.rankingOptIn() != null) user.setRankingOptIn(request.rankingOptIn());
        return new MeResponse(user);
    }

    private User findUser(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("사용자"));
    }

    private TokenResponse issueToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject(user.getId().toString())
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .build();
        String token =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        return new TokenResponse(token, expiresAt, new MeResponse(user));
    }
}
