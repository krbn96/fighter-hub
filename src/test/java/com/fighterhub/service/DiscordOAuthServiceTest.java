package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fighterhub.config.DiscordOAuthProperties;
import com.fighterhub.config.FrontendProperties;
import com.fighterhub.discord.DiscordApiClient;
import com.fighterhub.discord.DiscordProviderException;
import com.fighterhub.discord.DiscordUserResponse;
import com.fighterhub.security.OAuthStateExpiredException;
import com.fighterhub.security.OAuthStateInvalidException;
import com.fighterhub.security.OAuthStateProvider;

@ExtendWith(MockitoExtension.class)
class DiscordOAuthServiceTest {

    @Mock
    private DiscordApiClient discordApiClient;

    @Mock
    private OAuthStateProvider oAuthStateProvider;

    @Mock
    private UserService userService;

    private DiscordOAuthProperties discordOAuthProperties;
    private FrontendProperties frontendProperties;

    private DiscordOAuthService discordOAuthService;

    @BeforeEach
    void setUp() {
        discordOAuthProperties = new DiscordOAuthProperties(
                "test-client-id", "test-client-secret",
                "http://localhost:8080/api/oauth/discord/callback");
        frontendProperties = new FrontendProperties("http://localhost:5173");

        discordOAuthService = new DiscordOAuthService(
                discordApiClient, oAuthStateProvider, userService,
                discordOAuthProperties, frontendProperties);
    }

    @Test
    void createAuthorizationUrl_client_id_scope_redirect_uri_stateを含むURLを生成する() {
        when(oAuthStateProvider.generate(1L)).thenReturn("signed-state-value");

        String url = discordOAuthService.createAuthorizationUrl(1L);

        assertTrue(url.startsWith("https://discord.com/oauth2/authorize?"));
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("scope=identify"));
        assertTrue(url.contains("state=signed-state-value"));
        // RFC 3986上、コロン・スラッシュはquery component内で許可された文字のため、
        // UriComponentsBuilder#encodeはこれらをパーセントエンコードしない(それでも
        // &・=で正しく区切られるため、redirect_uriの値として曖昧性なく解釈できる)。
        assertTrue(url.contains("redirect_uri=http://localhost:8080/api/oauth/discord/callback"));

        // URL全体が構文上有効であること(URIとしてparseできること)を確認する。
        URI.create(url);
    }

    @Test
    void handleCallback_正常系_Discordアカウントを連携しsuccessへredirectする() {
        when(oAuthStateProvider.verify("valid-state"))
                .thenReturn(new OAuthStateProvider.StatePayload(1L, "discord-link", 9999999999L, "nonce"));
        when(discordApiClient.exchangeCodeForAccessToken("auth-code")).thenReturn("access-token");
        when(discordApiClient.fetchCurrentUser("access-token"))
                .thenReturn(new DiscordUserResponse("discord-id-1", "discorduser"));

        URI redirect = discordOAuthService.handleCallback("auth-code", "valid-state", null);

        verify(userService).linkDiscordAccount(1L, "discord-id-1", "discorduser");
        assertEquals("http://localhost:5173/mypage?oauth=discord&result=success", redirect.toString());
    }

    @Test
    void handleCallback_stateが不正な場合_invalid_stateへredirectしlinkDiscordAccountは呼ばれない() {
        when(oAuthStateProvider.verify("bad-state"))
                .thenThrow(new OAuthStateInvalidException("invalid"));

        URI redirect = discordOAuthService.handleCallback("auth-code", "bad-state", null);

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=invalid_state",
                redirect.toString());
        verify(userService, never()).linkDiscordAccount(any(), any(), any());
    }

    @Test
    void handleCallback_stateが期限切れの場合_expired_stateへredirectする() {
        when(oAuthStateProvider.verify("expired-state"))
                .thenThrow(new OAuthStateExpiredException("expired"));

        URI redirect = discordOAuthService.handleCallback("auth-code", "expired-state", null);

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=expired_state",
                redirect.toString());
    }

    @Test
    void handleCallback_Discordがaccess_deniedを返した場合_cancelledへredirectする() {
        when(oAuthStateProvider.verify("valid-state"))
                .thenReturn(new OAuthStateProvider.StatePayload(1L, "discord-link", 9999999999L, "nonce"));

        URI redirect = discordOAuthService.handleCallback(null, "valid-state", "access_denied");

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=cancelled",
                redirect.toString());
        verify(discordApiClient, never()).exchangeCodeForAccessToken(any());
    }

    @Test
    void handleCallback_Discordがaccess_denied以外のerrorを返した場合_provider_errorへredirectする() {
        when(oAuthStateProvider.verify("valid-state"))
                .thenReturn(new OAuthStateProvider.StatePayload(1L, "discord-link", 9999999999L, "nonce"));

        URI redirect = discordOAuthService.handleCallback(null, "valid-state", "server_error");

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=provider_error",
                redirect.toString());
    }

    @Test
    void handleCallback_codeが無い場合_provider_errorへredirectする() {
        when(oAuthStateProvider.verify("valid-state"))
                .thenReturn(new OAuthStateProvider.StatePayload(1L, "discord-link", 9999999999L, "nonce"));

        URI redirect = discordOAuthService.handleCallback(null, "valid-state", null);

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=provider_error",
                redirect.toString());
    }

    @Test
    void handleCallback_token交換に失敗した場合_provider_errorへredirectする() {
        when(oAuthStateProvider.verify("valid-state"))
                .thenReturn(new OAuthStateProvider.StatePayload(1L, "discord-link", 9999999999L, "nonce"));
        when(discordApiClient.exchangeCodeForAccessToken("auth-code"))
                .thenThrow(new DiscordProviderException("token exchange failed"));

        URI redirect = discordOAuthService.handleCallback("auth-code", "valid-state", null);

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=provider_error",
                redirect.toString());
        verify(userService, never()).linkDiscordAccount(any(), any(), any());
    }

    @Test
    void handleCallback_usersMe取得に失敗した場合_provider_errorへredirectする() {
        when(oAuthStateProvider.verify("valid-state"))
                .thenReturn(new OAuthStateProvider.StatePayload(1L, "discord-link", 9999999999L, "nonce"));
        when(discordApiClient.exchangeCodeForAccessToken("auth-code")).thenReturn("access-token");
        when(discordApiClient.fetchCurrentUser("access-token"))
                .thenThrow(new DiscordProviderException("fetch failed"));

        URI redirect = discordOAuthService.handleCallback("auth-code", "valid-state", null);

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=provider_error",
                redirect.toString());
    }

    @Test
    void handleCallback_予期しない例外の場合_server_errorへredirectする() {
        when(oAuthStateProvider.verify("valid-state"))
                .thenReturn(new OAuthStateProvider.StatePayload(1L, "discord-link", 9999999999L, "nonce"));
        when(discordApiClient.exchangeCodeForAccessToken("auth-code")).thenReturn("access-token");
        when(discordApiClient.fetchCurrentUser("access-token"))
                .thenReturn(new DiscordUserResponse("discord-id-1", "discorduser"));
        doThrow(new RuntimeException("unexpected"))
                .when(userService).linkDiscordAccount(eq(1L), any(), any());

        URI redirect = discordOAuthService.handleCallback("auth-code", "valid-state", null);

        assertEquals(
                "http://localhost:5173/mypage?oauth=discord&result=error&reason=server_error",
                redirect.toString());
    }
}
