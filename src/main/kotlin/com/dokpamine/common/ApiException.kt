package com.dokpamine.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message) {
    companion object {
        fun notFound(what: String) = ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "$what 을(를) 찾을 수 없어요.")
        fun forbidden() = ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없어요.")
        fun badRequest(message: String) = ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message)
        fun conflict(code: String, message: String) = ApiException(HttpStatus.CONFLICT, code, message)
    }
}

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.status).body(ErrorResponse(e.code, e.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse("VALIDATION_FAILED", message))
    }
}
