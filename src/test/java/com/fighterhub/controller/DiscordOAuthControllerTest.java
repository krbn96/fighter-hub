package com.fighterhub.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.config.SecurityConfig;
import com.fighterhub.service.DiscordOAuthService;

@WebMvcTest(DiscordOAuthController.class)
@Import(SecurityConfig.class)
class DiscordOAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiscordOAuthService discordOAuthService;

    // SecurityFilterChainがJwtDecoderを要求するためコンテキスト起動に必要。
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static Jwt validJwt(String subject) {
        return Jwt.withTokenValue("valid-jwt-token")
                .header("alg", "HS256")
                .claim("sub", subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void authorize_JWTありの場合_200を返しJWTのsubjectのuserIdでauthorizationUrlが発行される() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(discordOAuthService.createAuthorizationUrl(1L))
                .thenReturn("https://discord.com/oauth2/authorize?client_id=test");

        mockMvc.perform(post("/api/oauth/discord/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl")
                        .value("https://discord.com/oauth2/authorize?client_id=test"));
    }

    @Test
    void authorize_JWTなしの場合_401を返しauthorizationUrlは発行されない() throws Exception {
        mockMvc.perform(post("/api/oauth/discord/authorize"))
                .andExpect(status().isUnauthorized());

        verify(discordOAuthService, never()).createAuthorizationUrl(eq(1L));
    }

    @Test
    void callback_認証なしで呼び出せ302でFrontendへredirectする() throws Exception {
        URI redirectUri = URI.create("http://localhost:5173/mypage?oauth=discord&result=success");
        when(discordOAuthService.handleCallback("auth-code", "valid-state", null))
                .thenReturn(redirectUri);

        mockMvc.perform(get("/api/oauth/discord/callback")
                        .param("code", "auth-code")
                        .param("state", "valid-state"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:5173/mypage?oauth=discord&result=success"));
    }

    @Test
    void callback_errorパラメータ付きでも302でFrontendへredirectする() throws Exception {
        URI redirectUri = URI.create(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=cancelled");
        when(discordOAuthService.handleCallback(null, "valid-state", "access_denied"))
                .thenReturn(redirectUri);

        mockMvc.perform(get("/api/oauth/discord/callback")
                        .param("state", "valid-state")
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://localhost:5173/mypage?oauth=discord&result=error&reason=cancelled"));
    }
}
