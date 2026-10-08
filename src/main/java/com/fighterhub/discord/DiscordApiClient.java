package com.fighterhub.discord;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fighterhub.config.DiscordOAuthProperties;

// DiscordのOAuth token endpoint / /users/@me への通信のみを担当する。
// 新規ライブラリは追加せず、既存依存(spring-boot-starter-webmvc経由のspring-web RestClient、
// jackson-databind)のみで実装している。access/refresh tokenはこのクラスの外へ永続化しない。
//
// Discord側が低速・無応答の場合にリクエストスレッドを長時間専有しないよう、
// connect timeout 5秒 / read timeout 10秒を明示設定する(Day 7調査で指摘された、
// デフォルト設定のままだとタイムアウトが無期限に近いという問題への対応)。
// 既存依存(spring-web)のSimpleClientHttpRequestFactoryのみを使用し、新規ライブラリは追加しない。
@Component
public class DiscordApiClient {

    private static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String USERS_ME_URL = "https://discord.com/api/users/@me";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient = RestClient.builder()
            .requestFactory(createRequestFactory())
            .build();
    private final DiscordOAuthProperties discordOAuthProperties;

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    public DiscordApiClient(DiscordOAuthProperties discordOAuthProperties) {
        this.discordOAuthProperties = discordOAuthProperties;
    }

    public String exchangeCodeForAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", discordOAuthProperties.redirectUri());
        form.add("client_id", discordOAuthProperties.clientId());
        form.add("client_secret", discordOAuthProperties.clientSecret());

        DiscordTokenResponse response;
        try {
            response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(DiscordTokenResponse.class);
        } catch (RestClientException ex) {
            throw new DiscordProviderException("Failed to exchange authorization code with Discord.", ex);
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new DiscordProviderException("Discord token response did not contain an access token.");
        }

        return response.accessToken();
    }

    public DiscordUserResponse fetchCurrentUser(String accessToken) {
        DiscordUserResponse response;
        try {
            response = restClient.get()
                    .uri(USERS_ME_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(DiscordUserResponse.class);
        } catch (RestClientException ex) {
            throw new DiscordProviderException("Failed to fetch Discord user profile.", ex);
        }

        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new DiscordProviderException("Discord user response did not contain an id.");
        }

        return response;
    }
}
