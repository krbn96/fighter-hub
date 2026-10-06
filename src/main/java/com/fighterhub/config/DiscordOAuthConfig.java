package com.fighterhub.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Discordアカウント連携(OAuth)専用の設定。JwtConfig(ログイン用JWT)とは完全に分離し、
// OAUTH_STATE_SECRET専用のSecretKeyのみを提供する。既存のJwtEncoder/JwtDecoder/JwtProviderは
// OAuth state用には一切使用しない。
@EnableConfigurationProperties({
    DiscordOAuthProperties.class,
    OAuthStateProperties.class,
    FrontendProperties.class
})
@Configuration
public class DiscordOAuthConfig {

    // HMAC-SHA256には256bit(32byte)以上の鍵長が必要(JwtConfigと同じ閾値)。
    static final int MINIMUM_SECRET_KEY_BYTES = 32;

    @Bean
    public SecretKey oauthStateSecretKey(OAuthStateProperties oAuthStateProperties) {
        return resolveSecretKey(oAuthStateProperties.secret());
    }

    // OAUTH_STATE_SECRETの設定不備を、起動時に分かりやすいメッセージで検出する。
    // secretの実際の値・decode結果・環境変数の内容はメッセージへ一切含めない。
    static SecretKey resolveSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "OAuth state secret configuration is invalid. "
                            + "The OAUTH_STATE_SECRET environment variable is not set. "
                            + "Set it to a Base64-encoded value of at least "
                            + MINIMUM_SECRET_KEY_BYTES + " bytes (256 bits).");
        }

        if (secret.contains("${") || secret.contains("}")) {
            throw new IllegalStateException(
                    "OAuth state secret configuration is invalid. "
                            + "The OAUTH_STATE_SECRET environment variable appears to be unresolved "
                            + "(placeholder syntax detected). "
                            + "Ensure the OAUTH_STATE_SECRET environment variable is actually set.");
        }

        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "OAuth state secret configuration is invalid. "
                            + "The OAUTH_STATE_SECRET environment variable must be a valid Base64-encoded string.");
        }

        if (secretBytes.length < MINIMUM_SECRET_KEY_BYTES) {
            throw new IllegalStateException(
                    "OAuth state secret configuration is invalid. "
                            + "The decoded OAUTH_STATE_SECRET must be at least "
                            + MINIMUM_SECRET_KEY_BYTES + " bytes (256 bits).");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
