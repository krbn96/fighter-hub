package com.fighterhub.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

// Swagger UIの「Authorize」ボタンからJWTを設定できるようにするための
// Bearer JWT Security Scheme定義。SecurityFilterChainの認証仕様には影響しない。
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@Configuration
public class OpenApiConfig {
}
