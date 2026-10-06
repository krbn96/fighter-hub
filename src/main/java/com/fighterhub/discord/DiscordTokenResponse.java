package com.fighterhub.discord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Discord token endpoint(POST https://discord.com/api/oauth2/token)のレスポンスのうち、
// 実際に使用するaccess_tokenのみを保持する。refresh_token等は読み取っても一切保持・永続化しない。
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscordTokenResponse(
    @JsonProperty("access_token")
    String accessToken
) {
}
