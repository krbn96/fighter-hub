package com.fighterhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth.state")
public record OAuthStateProperties(
    String secret
) {
}
