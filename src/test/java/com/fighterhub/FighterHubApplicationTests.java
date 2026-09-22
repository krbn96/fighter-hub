package com.fighterhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// TEST_ONLY_JWT_SECRET: ApplicationContext起動確認のためだけに使用する、
// テストコード内固定のダミー値（全byteゼロ）。本番のJWT_SECRET環境変数とは無関係で、
// 本番運用では一切使用しない。OSの環境変数やapplication.yamlは変更していない。
@SpringBootTest(properties = {
	"jwt.secret=" + FighterHubApplicationTests.TEST_ONLY_JWT_SECRET
})
class FighterHubApplicationTests {

	// Base64エンコード済み・32byte(256bit)、全byteゼロのテスト専用ダミー値。
	static final String TEST_ONLY_JWT_SECRET =
			"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

	@Test
	void contextLoads() {
	}

}
