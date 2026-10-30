package com.dokpamine.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig(private val jwtProperties: JwtProperties) {

    private val secretKey by lazy {
        val bytes = jwtProperties.secret.toByteArray()
        require(bytes.size >= 32) { "dokpamine.jwt.secret 은 32바이트 이상이어야 합니다." }
        SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers -> headers.frameOptions { it.sameOrigin() } }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                it.requestMatchers("/actuator/health", "/h2-console/**").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build()
}

/** JWT subject 에 담긴 사용자 ID. 컨트롤러에서 `auth.userId()` 로 꺼내 쓴다. */
fun Authentication.userId(): UUID = UUID.fromString(name)
