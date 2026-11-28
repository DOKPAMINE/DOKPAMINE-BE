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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
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
        String username = request.username().toLowerCase(Locale.ROOT);
        String nickname = TextValues.trim(request.nickname());
        if (users.existsByUsername(username)) throw usernameTaken();
        if (users.existsByNickname(nickname)) throw nicknameTaken();
        User user;
        try {
            // FR-10: 사전 조회를 함께 통과한 가입도 토큰 발급 전에 UNIQUE 충돌을 확인한다.
            user =
                    users.saveAndFlush(
                            new User(
                                    UUID.randomUUID(),
                                    username,
                                    passwords.encode(request.password()),
                                    nickname));
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e))
                throw isNicknameViolation(e) ? nicknameTaken() : usernameTaken();
            throw e;
        }
        return issueToken(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username().toLowerCase(Locale.ROOT));
        if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 맞지 않아요.");
        }
        return issueToken(user);
    }

    public MeResponse me(UUID id) {
        return new MeResponse(findUser(id));
    }

    @Transactional
    public MeResponse updateMe(UUID id, UpdateMeRequest request) {
        User user = findUser(id);
        if (request.nickname() != null) {
            String nickname = TextValues.trim(request.nickname());
            // FR-10: 내 현재 닉네임을 다시 보내는 요청은 중복으로 보지 않는다.
            if (users.existsByNicknameAndIdNot(nickname, id)) throw nicknameTaken();
            user.setNickname(nickname);
        }
        if (request.rankingOptIn() != null) user.setRankingOptIn(request.rankingOptIn());
        try {
            // FR-10: 동시에 같은 닉네임으로 바꾼 요청도 커밋 전에 409로 응답한다.
            users.flush();
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e) && isNicknameViolation(e)) throw nicknameTaken();
            throw e;
        }
        return new MeResponse(user);
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        return e.getMostSpecificCause() instanceof SQLException sql
                && "23505".equals(sql.getSQLState());
    }

    // H2와 PostgreSQL 모두 위반한 제약 이름을 메시지에 담으므로 이름으로 어느 열인지 구분한다.
    private static boolean isNicknameViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("uk_users_nickname");
    }

    private static ApiException usernameTaken() {
        return ApiException.conflict("USERNAME_TAKEN", "이미 사용 중인 아이디예요.");
    }

    private static ApiException nicknameTaken() {
        return ApiException.conflict("NICKNAME_TAKEN", "이미 사용 중인 닉네임이에요.");
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
