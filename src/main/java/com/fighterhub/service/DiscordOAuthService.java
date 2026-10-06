package com.fighterhub.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fighterhub.config.DiscordOAuthProperties;
import com.fighterhub.config.FrontendProperties;
import com.fighterhub.discord.DiscordApiClient;
import com.fighterhub.discord.DiscordProviderException;
import com.fighterhub.discord.DiscordUserResponse;
import com.fighterhub.security.OAuthStateExpiredException;
import com.fighterhub.security.OAuthStateInvalidException;
import com.fighterhub.security.OAuthStateProvider;

// Discordアカウント連携のOAuth Authorization Code Flow全体(state生成、Authorization URL構築、
// callbackでのstate検証・token交換・/users/@me取得・リンク処理呼び出し・Frontendへのredirect
// URL組み立て)を担当する。DB更新そのものはUserService#linkDiscordAccountへ委譲する
// (トランザクション境界はUserService側に置く)。
//
// callbackは常にFrontendへのredirect URIを返す(例外を外へ投げない)。Discordから返された
// error_description、Java例外メッセージ、state全文、access token等は一切redirect URLへ
// 含めない(固定のreasonコードのみを使用する)。
@Service
public class DiscordOAuthService {

    private static final Logger log = LoggerFactory.getLogger(DiscordOAuthService.class);

    private static final String DISCORD_AUTHORIZE_URL = "https://discord.com/oauth2/authorize";
    private static final String DISCORD_SCOPE = "identify";
    private static final String ACCESS_DENIED_ERROR = "access_denied";

    private final DiscordApiClient discordApiClient;
    private final OAuthStateProvider oAuthStateProvider;
    private final UserService userService;
    private final DiscordOAuthProperties discordOAuthProperties;
    private final FrontendProperties frontendProperties;

    public DiscordOAuthService(
            DiscordApiClient discordApiClient,
            OAuthStateProvider oAuthStateProvider,
            UserService userService,
            DiscordOAuthProperties discordOAuthProperties,
            FrontendProperties frontendProperties) {
        this.discordApiClient = discordApiClient;
        this.oAuthStateProvider = oAuthStateProvider;
        this.userService = userService;
        this.discordOAuthProperties = discordOAuthProperties;
        this.frontendProperties = frontendProperties;
    }

    public String createAuthorizationUrl(Long userId) {
        String state = oAuthStateProvider.generate(userId);

        return UriComponentsBuilder.fromUriString(DISCORD_AUTHORIZE_URL)
                .queryParam("client_id", discordOAuthProperties.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", discordOAuthProperties.redirectUri())
                .queryParam("scope", DISCORD_SCOPE)
                .queryParam("state", state)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    public URI handleCallback(String code, String state, String error) {
        Long userId;
        try {
            userId = oAuthStateProvider.verify(state).userId();
        } catch (OAuthStateExpiredException ex) {
            log.warn("Discord OAuth callback rejected: reason=expired_state");
            return redirectTo("error", "expired_state");
        } catch (OAuthStateInvalidException ex) {
            log.warn("Discord OAuth callback rejected: reason=invalid_state");
            return redirectTo("error", "invalid_state");
        }

        if (error != null) {
            if (ACCESS_DENIED_ERROR.equals(error)) {
                log.warn("Discord OAuth callback rejected: reason=cancelled");
                return redirectTo("error", "cancelled");
            }
            log.warn("Discord OAuth callback rejected: reason=provider_error");
            return redirectTo("error", "provider_error");
        }

        if (code == null || code.isBlank()) {
            log.warn("Discord OAuth callback rejected: reason=provider_error");
            return redirectTo("error", "provider_error");
        }

        try {
            String accessToken = discordApiClient.exchangeCodeForAccessToken(code);
            DiscordUserResponse discordUser = discordApiClient.fetchCurrentUser(accessToken);

            userService.linkDiscordAccount(userId, discordUser.id(), discordUser.username());

            return redirectTo("success", null);
        } catch (DiscordProviderException ex) {
            log.warn("Discord OAuth callback failed: reason=provider_error");
            return redirectTo("error", "provider_error");
        } catch (Exception ex) {
            log.warn("Discord OAuth callback failed: reason=server_error");
            return redirectTo("error", "server_error");
        }
    }

    private URI redirectTo(String result, String reason) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(frontendProperties.baseUrl())
                .path("/mypage")
                .queryParam("oauth", "discord")
                .queryParam("result", result);

        if (reason != null) {
            builder.queryParam("reason", reason);
        }

        return builder.build().toUri();
    }
}
