package com.dokpamine.global.jwt;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class AuthenticatedUser {
    private AuthenticatedUser() {}

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
