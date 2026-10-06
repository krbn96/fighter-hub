package com.fighterhub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

// Discordアカウント連携専用の、署名付き短寿命state実装。
// 既存のログイン用JwtEncoder/JwtDecoder/JwtProvider(JwtConfig/JwtProvider)とは完全に分離し、
// OAUTH_STATE_SECRET専用のSecretKey(DiscordOAuthConfig#oauthStateSecretKey)のみを使用する。
// Spring Session・DB stateテーブルは使用せず、署名付きトークン自体にpayloadを持たせることで
// 既存のstateless構成を維持する。
@Component
public class OAuthStateProvider {

    public static final String DISCORD_LINK_PURPOSE = "discord-link";

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final int NONCE_BYTES = 16;

    private final SecretKey secretKey;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthStateProvider(
            @Qualifier("oauthStateSecretKey") SecretKey secretKey,
            Clock clock) {
        this.secretKey = secretKey;
        this.clock = clock;
    }

    public record StatePayload(Long userId, String purpose, long exp, String nonce) {
    }

    // payload(JSON)をBase64url化した部分 + "." + その部分に対するHMAC-SHA256署名(Base64url)。
    // state自体にDiscordアカウント連携対象のuserIdを保持させることで、Discordからの
    // callbackリクエスト(Authorizationヘッダーを持たない)でもどのFIGHTER HUBユーザーの
    // 連携操作かを安全に復元できる。
    public String generate(Long userId) {
        byte[] nonceBytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonceBytes);
        String nonce = base64UrlEncode(nonceBytes);

        long exp = clock.instant().plus(STATE_TTL).getEpochSecond();
        StatePayload payload = new StatePayload(userId, DISCORD_LINK_PURPOSE, exp, nonce);

        String payloadSegment = base64UrlEncode(objectMapper.writeValueAsBytes(payload));
        String signatureSegment = sign(payloadSegment);

        return payloadSegment + "." + signatureSegment;
    }

    // 署名・expiration・purposeを必ず検証する。state全文やSecretはここでも含めて
    // 例外メッセージ・ログへ一切出力しない。
    public StatePayload verify(String state) {
        if (state == null || state.isBlank()) {
            throw new OAuthStateInvalidException("OAuth state is missing.");
        }

        String[] parts = state.split("\\.", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new OAuthStateInvalidException("OAuth state format is invalid.");
        }

        String payloadSegment = parts[0];
        String signatureSegment = parts[1];

        String expectedSignature = sign(payloadSegment);
        if (!constantTimeEquals(expectedSignature, signatureSegment)) {
            throw new OAuthStateInvalidException("OAuth state signature is invalid.");
        }

        StatePayload payload = readPayload(payloadSegment);

        if (!DISCORD_LINK_PURPOSE.equals(payload.purpose())) {
            throw new OAuthStateInvalidException("OAuth state purpose is invalid.");
        }

        if (payload.userId() == null) {
            throw new OAuthStateInvalidException("OAuth state userId is missing.");
        }

        // 締切ちょうど(now == exp)は期限切れ扱い(既存のisRecruitmentOpenと同じ境界の考え方)。
        if (clock.instant().getEpochSecond() >= payload.exp()) {
            throw new OAuthStateExpiredException("OAuth state has expired.");
        }

        return payload;
    }

    private String sign(String payloadSegment) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign OAuth state.", ex);
        }
    }

    private StatePayload readPayload(String payloadSegment) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(payloadSegment);
            return objectMapper.readValue(decoded, StatePayload.class);
        } catch (Exception ex) {
            throw new OAuthStateInvalidException("OAuth state payload is malformed.");
        }
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // タイミング攻撃を避けるため、署名比較はMessageDigest.isEqualによる定数時間比較で行う。
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
