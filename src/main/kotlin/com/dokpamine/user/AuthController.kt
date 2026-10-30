package com.dokpamine.user

import com.dokpamine.common.ApiException
import com.dokpamine.config.JwtProperties
import com.dokpamine.config.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class SignupRequest(
    @field:Email @field:Size(max = 255) val email: String,
    @field:Size(min = 8, max = 64) val password: String,
    @field:Size(min = 1, max = 30) val nickname: String,
)

data class LoginRequest(val email: String, val password: String)

data class MeResponse(val id: UUID, val email: String, val nickname: String, val rankingOptIn: Boolean) {
    constructor(user: User) : this(user.id, user.email, user.nickname, user.rankingOptIn)
}

data class TokenResponse(val accessToken: String, val expiresAt: Instant, val user: MeResponse)

data class UpdateMeRequest(
    @field:Size(min = 1, max = 30) val nickname: String? = null,
    val rankingOptIn: Boolean? = null,
)

@RestController
@RequestMapping("/api")
class AuthController(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
) {

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun signup(@Valid @RequestBody req: SignupRequest): TokenResponse {
        val email = req.email.trim().lowercase()
        if (users.existsByEmail(email)) throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일이에요.")
        val user = users.save(
            User(
                id = UUID.randomUUID(),
                email = email,
                passwordHash = passwordEncoder.encode(req.password)!!,
                nickname = req.nickname.trim(),
            ),
        )
        return issueToken(user)
    }

    @PostMapping("/auth/login")
    fun login(@RequestBody req: LoginRequest): TokenResponse {
        val user = users.findByEmail(req.email.trim().lowercase())
        if (user == null || !passwordEncoder.matches(req.password, user.passwordHash)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않아요.")
        }
        return issueToken(user)
    }

    @GetMapping("/me")
    fun me(auth: Authentication): MeResponse = MeResponse(findUser(auth))

    @PatchMapping("/me")
    @Transactional
    fun updateMe(auth: Authentication, @Valid @RequestBody req: UpdateMeRequest): MeResponse {
        val user = findUser(auth)
        req.nickname?.let { user.nickname = it.trim() }
        req.rankingOptIn?.let { user.rankingOptIn = it }
        return MeResponse(user)
    }

    private fun findUser(auth: Authentication): User =
        users.findById(auth.userId()).orElseThrow { ApiException.notFound("사용자") }

    private fun issueToken(user: User): TokenResponse {
        val now = Instant.now()
        val expiresAt = now.plus(jwtProperties.ttl)
        val claims = JwtClaimsSet.builder()
            .subject(user.id.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return TokenResponse(token, expiresAt, MeResponse(user))
    }
}
