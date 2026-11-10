package com.dokpamine.domain.user.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, METHOD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = BcryptPasswordLength.Validator.class)
public @interface BcryptPasswordLength {
    String message() default "비밀번호를 UTF-8 기준 72바이트 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BcryptPasswordLength, String> {
        private static final int MAX_PASSWORD_BYTES = 72;

        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
        }
    }
}
