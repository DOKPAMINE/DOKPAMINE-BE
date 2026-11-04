package com.dokpamine.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("dokpamine.catalog")
public record CatalogProperties(
        String nlkCertKey, String kakaoRestApiKey, String googleBooksKey, Duration timeout) {
    public CatalogProperties {
        if (nlkCertKey == null) nlkCertKey = "";
        if (kakaoRestApiKey == null) kakaoRestApiKey = "";
        if (googleBooksKey == null) googleBooksKey = "";
        if (timeout == null) timeout = Duration.ofSeconds(4);
    }
}
