package com.fighterhub.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.UserRepository;
import com.fighterhub.service.UserService;

// PATCH /api/users/meで変更した内容が、別のHTTPリクエストであるGET /api/users/meで
// 正しく読み取れることを実際のFilter Chain・実DB(PostgreSQL)経由で確認する回帰テスト。
// Mockitoベースの単体テストでは検出できない、Controller〜Service〜DB間の
// 永続化不具合(dirty checkingが効かない等)を検出する目的で追加した。
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.secret=" + UserMePatchPersistenceTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class UserMePatchPersistenceTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private Long createdUserId;

    @AfterEach
    void cleanUp() {
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
        }
    }

    @Test
    void PATCH_apiUsersMeで変更したmessageが別のGETリクエストでも読み取れる() throws Exception {
        List<Character> characters = characterRepository.findAll();
        Assumptions.assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないためスキップする");
        Long characterId = characters.get(0).getId();

        UserCreateRequest createRequest = new UserCreateRequest(
                "patch-persistence-" + UUID.randomUUID() + "@example.com",
                "password123",
                "PATCH Persistence Test User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                "よろしくお願いします"
        );
        UserCreateResponse created = userService.createUser(createRequest);
        createdUserId = created.id();

        String token = generateToken(createdUserId);

        MvcResult beforePatchResult = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String updatedAtBeforePatch = JsonPath.read(
                beforePatchResult.getResponse().getContentAsString(), "$.updatedAt");

        String patchBody = """
                {
                  "message": "FIGHTER HUBを開発中です"
                }
                """;

        MvcResult patchResult = mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("FIGHTER HUBを開発中です"))
                .andReturn();
        String updatedAtInPatchResponse = JsonPath.read(
                patchResult.getResponse().getContentAsString(), "$.updatedAt");

        // PATCH Response自体のupdatedAtが、flush前の古い値のまま返っていないことを確認する
        // (Hibernateの@UpdateTimestampはflush時に反映されるため、flushせずにレスポンスを
        // 生成すると更新前の値が返ってしまう回帰がある)。
        assertNotEquals(updatedAtBeforePatch, updatedAtInPatchResponse);

        // PATCHとは別のHTTPリクエストとしてGETを送信し、DBへ永続化された値が
        // 読み取れることを確認する。
        MvcResult afterPatchResult = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("FIGHTER HUBを開発中です"))
                .andReturn();
        String updatedAtAfterPatch = JsonPath.read(
                afterPatchResult.getResponse().getContentAsString(), "$.updatedAt");

        assertEquals(updatedAtInPatchResponse, updatedAtAfterPatch);
    }

    private String generateToken(Long userId) {
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
