package com.dokpamine.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("dokpamine.jwt")
data class JwtProperties(
    val secret: String,
    val ttl: Duration,
)

@ConfigurationProperties("dokpamine.catalog")
data class CatalogProperties(
    val nlkCertKey: String = "",
    val kakaoRestApiKey: String = "",
    val googleBooksKey: String = "",
    val timeout: Duration = Duration.ofSeconds(4),
)
