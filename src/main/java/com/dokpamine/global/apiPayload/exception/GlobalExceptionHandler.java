package com.dokpamine.global.apiPayload.exception;

import com.dokpamine.global.apiPayload.response.ErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getAllErrors().stream()
                        .map(DefaultMessageSourceResolvable::getDefaultMessage)
                        .filter(Objects::nonNull)
                        .filter(value -> !value.isBlank())
                        // 검증 순서에 관계없이 한 문장만 안내한다.
                        .sorted()
                        .findFirst()
                        .orElse("입력값을 확인해 주세요.");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", message));
    }

    // 앱은 code로 오류를 구분하고 message를 그대로 보여 주므로 Spring 기본 오류 본문을 내보내지 않는다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "요청 형식을 확인해 주세요."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "요청 값의 형식을 확인해 주세요."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "필수 요청 값을 입력해 주세요."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "요청한 주소를 찾을 수 없어요."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(e.getHeaders())
                .body(new ErrorResponse("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식이에요."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(e.getHeaders())
                .body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "요청 형식을 확인해 주세요."));
    }

    /**
     * #93 그 밖의 예외도 계약의 {code, message} 본문으로 응답한다. Spring MVC 예외는 원래 상태 코드를 유지하고,
     * 예상하지 못한 예외는 원인을 로그에만 남기고 500으로 안내한다(내부 메시지를 응답에 노출하지 않는다).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) throws Exception {
        // 인증·권한 오류는 Spring Security가 401·403으로 응답하도록 그대로 넘긴다.
        if (e instanceof AuthenticationException || e instanceof AccessDeniedException) throw e;
        if (e instanceof org.springframework.web.ErrorResponse spring
                && !spring.getStatusCode().is5xxServerError()) {
            HttpStatusCode status = spring.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            return ResponseEntity.status(status)
                    .headers(spring.getHeaders())
                    .body(new ErrorResponse(
                            resolved != null ? resolved.name() : "HTTP_" + status.value(),
                            "요청을 처리하지 못했어요. 다시 시도해 주세요."));
        }
        log.error("처리하지 못한 서버 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_ERROR", "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."));
    }
}
