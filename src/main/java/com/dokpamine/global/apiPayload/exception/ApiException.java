package com.dokpamine.global.apiPayload.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + objectParticle(what) + " 찾을 수 없어요.");
    }

    // 앱이 메시지를 그대로 보여 주므로 "감상 을(를)" 대신 받침에 맞는 조사를 붙인다.
    static String objectParticle(String word) {
        char last = word.isEmpty() ? ' ' : word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') return "을(를)";
        return (last - '가') % 28 == 0 ? "를" : "을";
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없어요.");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
