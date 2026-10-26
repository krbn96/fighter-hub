package com.fighterhub.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

// デフォルト(prodプロファイルを有効化しない)構成では、server.forward-headers-strategyが
// 未設定のためTomcatはX-Forwarded-Forを一切解釈しない。この構成で、クライアントが
// 任意に送るX-Forwarded-Forが実際に無視され、レート制限のIP判定を偽装できないことを
// 実際のHTTPリクエスト(embedded Tomcat)で検証する。
//
// 検証方法: 同一email・同一実接続(ローカルループバック)で、X-Forwarded-Forの値だけを
// 変えながらPOST /api/auth/loginを5回失敗させる。ヘッダーが信用されるなら「別IP」として
// comboキーが分かれブロックされないはずだが、本テスト(デフォルト構成)では無視されるため、
// 実際にはすべて同一の接続元として扱われ、5回目でブロック(429)される。
//
// 新規ライブラリを追加しないため、HTTPクライアントはJDK標準のjava.net.http.HttpClientを使う
// (TestRestTemplateはSpring Boot 4でspring-boot-resttestclientという別artifactへ分離され、
//  spring-boot-starter-testに含まれなくなったため、新規依存追加を避ける目的で使用しない)。
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "jwt.secret=" + LoginRateLimitClientIpDefaultProfileTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class LoginRateLimitClientIpDefaultProfileTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void forwardHeadersStrategy未設定の場合_XForwardedForを送ってもIP判定に反映されずcomboキーが1つにまとまる() throws Exception {
        String email = "ip-default-profile-test@example.com";

        // 5回失敗させる(comboキーの閾値ちょうど)。この5回はいずれも通常の401になる
        // (閾値到達の判定は「次の」リクエストで行われるため)。
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response = loginWithForwardedFor(email, "203.0.113." + (10 + i));
            assertEquals(401, response.statusCode());
        }

        // 6回目(別のX-Forwarded-For値)。ヘッダーが信用されていれば「初めてのIP」として
        // 許可されるはずだが、無視される設定のため、これまでと同一の接続元として扱われ、
        // comboキーの閾値(5回)に達しているため429になる。
        HttpResponse<String> sixth = loginWithForwardedFor(email, "198.51.100.77");
        assertEquals(429, sixth.statusCode());
        assertTrue(sixth.headers().firstValue("Retry-After").isPresent());
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
