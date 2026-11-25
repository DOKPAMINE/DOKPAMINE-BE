package com.dokpamine.global.jwt;

import com.dokpamine.domain.user.repository.UserRepository;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * FR-10: 서명과 만료가 정상이어도 토큰의 사용자가 DB에 없으면(DB 초기화·교체) 401로 막는다. 이 검사가 없으면 /api/me는
 * 404, 기록 동기화는 외래 키 위반 500이 나서 앱이 재로그인으로 돌아가지 못한다. 앱은 401을 받으면 로그아웃한다.
 */
public class ExistingUserValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2TokenValidatorResult UNKNOWN_USER =
            OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            OAuth2ErrorCodes.INVALID_TOKEN, "토큰의 사용자가 존재하지 않습니다.", null));
    private final UserRepository users;

    public ExistingUserValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        UUID id;
        try {
            id = UUID.fromString(token.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return UNKNOWN_USER;
        }
        return users.existsById(id) ? OAuth2TokenValidatorResult.success() : UNKNOWN_USER;
    }
}
