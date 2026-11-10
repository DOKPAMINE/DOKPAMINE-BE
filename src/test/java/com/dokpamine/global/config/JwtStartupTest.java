package com.dokpamine.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtStartupTest {
    private WebApplicationContextRunner context(String profile, String secret) {
        return new WebApplicationContextRunner()
                .withInitializer(context -> {
                    String sourceName = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
                    Map<String, Object> variables = secret == null
                            ? Map.of()
                            : Map.of("DOKPAMINE_JWT_SECRET", secret);
                    context.getEnvironment().getPropertySources().replace(
                            sourceName, new SystemEnvironmentPropertySource(sourceName, variables));
                    new ConfigDataApplicationContextInitializer().initialize(context);
                })
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(JwtTestConfiguration.class, SecurityConfig.class);
    }

    @ParameterizedTest(name = "운영 서명 키가 유효하지 않으면 시작에 실패한다 [{index}]")
    @NullAndEmptySource
    @ValueSource(strings = {"short", "                                "})
    void invalidProductionSecretStopsStartup(String secret) {
        context("prod", secret).run(result -> assertThat(result).hasFailed()
                .getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("DOKPAMINE_JWT_SECRET"));
    }

    @Test
    void productionStartsWithConfiguredSecret() {
        context("prod", "12345678901234567890123456789012").run(result ->
                assertThat(result).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class));
    }

    @Test
    void localStartsWithDevelopmentDefault() {
        context("local", null).run(result ->
                assertThat(result).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtTestConfiguration {}
}
