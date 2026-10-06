package com.fighterhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "discord")
public record DiscordOAuthProperties(
    String clientId,
    String clientSecret,
    String redirectUri
) {
}
