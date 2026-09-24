package com.fighterhub.config;

import java.time.Clock;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.fighterhub.security.JwtSubjectValidator;

// JWT生成・検証用のBean定義。既存SecurityConfig（SecurityFilterChain/PasswordEncoder）とは
// 別クラスとして分離し、SecurityFilterChainの挙動には一切影響しない。
@EnableConfigurationProperties(JwtProperties.class)
@Configuration
public class JwtConfig {

    // HS256には256bit(32byte)以上の鍵長が必要。
    static final int MINIMUM_SECRET_KEY_BYTES = 32;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // JwtEncoderとJwtDecoderが必ず同じ鍵を使うよう、SecretKeyをBean化して共有する。
    @Bean
    public SecretKey jwtSecretKey(JwtProperties jwtProperties) {
        return resolveSecretKey(jwtProperties.secret());
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // 既定のtimestamp検証(exp等)に加えて、subがFIGHTER HUBの前提(正のLong値)を
        // 満たすことをvalidatorとして合成する。署名検証・exp検証は変更しない。
        OAuth2TokenValidator<Jwt> withSubjectValidation =
                JwtValidators.createDefaultWithValidators(new JwtSubjectValidator());
        decoder.setJwtValidator(withSubjectValidation);

        return decoder;
    }

    // JWT_SECRETの設定不備を、起動時に分かりやすいメッセージで検出する。
    // secretの実際の値・decode結果・環境変数の内容はメッセージへ一切含めない。
    static SecretKey resolveSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret configuration is invalid. "
                            + "The JWT_SECRET environment variable is not set. "
                            + "Set it to a Base64-encoded value of at least "
                            + MINIMUM_SECRET_KEY_BYTES + " bytes (256 bits).");
        }

        if (secret.contains("${") || secret.contains("}")) {
            throw new IllegalStateException(
                    "JWT secret configuration is invalid. "
                            + "The JWT_SECRET environment variable appears to be unresolved "
                            + "(placeholder syntax detected). "
                            + "Ensure the JWT_SECRET environment variable is actually set.");
        }

        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "JWT secret configuration is invalid. "
                            + "The JWT_SECRET environment variable must be a valid Base64-encoded string.");
        }

        if (secretBytes.length < MINIMUM_SECRET_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT secret configuration is invalid. "
                            + "The decoded JWT_SECRET must be at least "
                            + MINIMUM_SECRET_KEY_BYTES + " bytes (256 bits) for HS256.");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
