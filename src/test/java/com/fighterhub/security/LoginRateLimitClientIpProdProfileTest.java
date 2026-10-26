package com.fighterhub.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// prodプロファイル(application-prod.yamlのserver.forward-headers-strategy: native)を有効化した
// 構成で、実際にX-Forwarded-Forが正しくクライアントIPとして解決されることを検証する。
//
// このテストの接続元(HttpClient)はループバック(127.0.0.1)であり、Tomcat RemoteIpValveの
// 既定のinternal-proxiesはプライベートIPレンジ(127.x含む)に一致するため、「backendの直接の
// 接続相手がnginxのような内部プロキシである」という本番の前提をローカルループバック接続で
// 模擬できる(本番でCaddy/nginxが実際にどう振る舞うかの実機検証ではない点はclaude-report.mdに
// 明記する)。
//
// 検証方法: 同一email・同一X-Forwarded-Forで5回失敗させ6回目が429になることを確認した後、
// 異なるX-Forwarded-For(別の「実クライアントIP」)で同じemailへ試行すると、comboキーの
// IP部分が変わるため429にならず通常の401になることを確認する
// (=ヘッダーの値が実際にIP判定へ反映されている証拠)。
//
// HTTPクライアントはJDK標準のjava.net.http.HttpClientを使う(新規依存ライブラリを
// 追加しないため。詳細はLoginRateLimitClientIpDefaultProfileTestのコメント参照)。
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "jwt.secret=" + LoginRateLimitClientIpProdProfileTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class LoginRateLimitClientIpProdProfileTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void forwardHeadersStrategyNative有効時は信頼できる接続元からのXForwardedForが実クライアントIPとして解決される() throws Exception {
        String email = "ip-prod-profile-test@example.com";
        String firstClientIp = "203.0.113.50, 172.20.0.5";
        String secondClientIp = "203.0.113.99, 172.20.0.5";

        for (int i = 0; i < 4; i++) {
            HttpResponse<String> response = loginWithForwardedFor(email, firstClientIp);
            assertEquals(401, response.statusCode());
        }

        HttpResponse<String> fifth = loginWithForwardedFor(email, firstClientIp);
        assertEquals(401, fifth.statusCode());

        HttpResponse<String> sixthSameIp = loginWithForwardedFor(email, firstClientIp);
        assertEquals(429, sixthSameIp.statusCode());

        // 同じemailだが、X-Forwarded-Forの実クライアントIP部分(先頭値)が異なるため、
        // comboキーが分かれブロックされない(=ヘッダーの値が正しく解決・反映されている)。
        HttpResponse<String> differentIp = loginWithForwardedFor(email, secondClientIp);
        assertEquals(401, differentIp.statusCode());
    }

    private HttpResponse<String> loginWithForwardedFor(String email, String forwardedFor) throws Exception {
        String body = """
                {"email": "%s", "password": "wrong-password"}
                """.formatted(email);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
