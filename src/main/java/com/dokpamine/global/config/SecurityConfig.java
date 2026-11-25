package com.dokpamine.global.config;

import com.dokpamine.domain.user.repository.UserRepository;
import com.dokpamine.global.jwt.ExistingUserValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Configuration
public class SecurityConfig {
    private final SecretKey secretKey;

    public SecurityConfig(JwtProperties properties) {
        String secret = properties.secret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 || secret.isBlank()) {
            throw new IllegalArgumentException("DOKPAMINE_JWT_SECRET에 UTF-8 기준 32바이트 이상의 서명 키를 설정해 주세요.");
        }
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login")
                                .permitAll()
                                .requestMatchers("/actuator/health", "/h2-console/**")
                                .permitAll()
                                // 토큰 없는 가입·로그인의 오류가 /error로 넘어가도 원래 상태 코드를 401로 가리지 않게 한다.
                                .requestMatchers("/error")
                                .permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/swagger-ui.html",
                                        "/swagger-ui/**",
                                        "/v3/api-docs",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs.yaml")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(UserRepository users) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        // 기본 만료 검사에 더해 토큰의 사용자가 지금도 있는지 확인한다(FR-10).
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(), new ExistingUserValidator(users)));
        return decoder;
    }
}
