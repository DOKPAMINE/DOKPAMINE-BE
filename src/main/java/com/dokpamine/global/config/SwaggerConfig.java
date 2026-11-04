package com.dokpamine.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    OpenAPI dokpamineOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("독파민 API")
                                .description("회원 인증, 도서 조회, 독서 기록 동기화, 공개 감상과 랭킹 API입니다.")
                                .version("0.1.0"))
                .addServersItem(new Server().url("/"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "BearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("회원가입·로그인 응답의 accessToken을 입력하세요.")));
    }
}
