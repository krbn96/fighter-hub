package com.fighterhub.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.DiscordAuthorizeResponse;
import com.fighterhub.service.DiscordOAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/oauth/discord")
public class DiscordOAuthController {

    private final DiscordOAuthService discordOAuthService;

    public DiscordOAuthController(DiscordOAuthService discordOAuthService) {
        this.discordOAuthService = discordOAuthService;
    }

    @Operation(
        summary = "Discordアカウント連携開始",
        description = "Discord OAuth Authorization Code Flowを開始するためのAuthorization URLを発行する。"
            + "ブラウザを直接302 redirectさせるのではなく、JSONでURLを返す(Authorizationヘッダーが必要なため)。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "発行成功",
            content = @Content(
                schema = @Schema(implementation = DiscordAuthorizeResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "サーバーエラー"
        )
    })
    @PostMapping("/authorize")
    public DiscordAuthorizeResponse authorize(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        String authorizationUrl = discordOAuthService.createAuthorizationUrl(userId);
        return new DiscordAuthorizeResponse(authorizationUrl);
    }

    @Operation(
        summary = "Discord OAuth callback",
        description = "Discordから呼び出されるcallback。state検証・token交換・アカウント連携を行い、"
            + "結果に応じてFrontend MY PAGEへ302 redirectする(JSONは返さない)。"
            + "認証不要(Discordからの未認証アクセスを前提とするため、このエンドポイントのみpermitAll)。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "302",
            description = "FRONTEND_BASE_URL配下の/mypageへredirect"
                + "(?oauth=discord&result=success、または&result=error&reason=<固定reason>)"
        )
    })
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        URI redirectUri = discordOAuthService.handleCallback(code, state, error);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
    }
}
