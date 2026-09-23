package com.fighterhub.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// 生成されたOpenAPI定義(/v3/api-docs)に、Swagger UIの「Authorize」ボタン用の
// bearerAuth Security Schemeが定義され、認証必須APIにのみ適用されていることを確認する。
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.secret=" + OpenApiConfigTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class OpenApiConfigTest {

    // TEST_ONLY_JWT_SECRET: このテストのApplicationContext起動のためだけのダミー値
    // (Base64, 32byte, 全byteゼロ)。本番のJWT_SECRET環境変数とは無関係。
    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_bearerAuthのSecuritySchemeがHTTP_Bearer_JWTとして定義されている() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void apiDocs_GET_apiUsersMeにbearerAuthが適用されている() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/users/me.get.security[0].bearerAuth").exists());
    }

    @Test
    void apiDocs_PATCH_apiUsersMeにbearerAuthが適用されている() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/users/me.patch.security[0].bearerAuth").exists());
    }

    @Test
    void apiDocs_公開APIにはbearerAuthが適用されていない() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/users.post.security").doesNotExist())
                .andExpect(jsonPath("$.paths./api/users/{id}.get.security").doesNotExist())
                .andExpect(jsonPath("$.paths./api/auth/login.post.security").doesNotExist())
                .andExpect(jsonPath("$.paths./api/characters.get.security").doesNotExist())
                .andExpect(jsonPath("$.paths./api/characters/{id}.get.security").doesNotExist());
    }
}
