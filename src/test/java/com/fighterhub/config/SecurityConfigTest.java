package com.fighterhub.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.controller.RecruitmentApplicationController;
import com.fighterhub.controller.RecruitmentApplicationMeController;
import com.fighterhub.controller.TeamController;
import com.fighterhub.controller.TournamentController;
import com.fighterhub.controller.UserController;
import com.fighterhub.dto.UserMeResponse;
import com.fighterhub.service.RecruitmentApplicationService;
import com.fighterhub.service.TeamService;
import com.fighterhub.service.TournamentService;
import com.fighterhub.service.UserService;

// SecurityConfigのOAuth2 Resource Server(JWT Bearer)統合を、実際のFilter Chainを通して検証する。
// GET /api/users/{id:[0-9]+}のみpermitAllのため、GET /api/users/meはanyRequest().authenticated()
// の対象になる。UserController#findMe(@AuthenticationPrincipal Jwt)へ接続される実在のパスである。
// TeamController/RecruitmentApplicationController/RecruitmentApplicationMeController/TournamentControllerも
// スライスに含め、/api/teams・/api/applications・/api/tournaments系のmatcher確認を実在のハンドラー経由(200)で
// 行えるようにする(未マッピングパスだとNoHandlerFoundExceptionがGlobalExceptionHandlerの汎用ハンドラーに
// 捕捉され500になり、permitAllの確認として不適切なため)。
@WebMvcTest({
    UserController.class,
    TeamController.class,
    RecruitmentApplicationController.class,
    RecruitmentApplicationMeController.class,
    TournamentController.class
})
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=" + SecurityConfigTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class SecurityConfigTest {

    // TEST_ONLY_JWT_SECRET: このテストのApplicationContext内でJwtEncoder/JwtDecoderを
    // 起動させるためだけのダミー値(Base64, 32byte, 全byteゼロ)。本番のJWT_SECRET環境変数
    // やapplication.yamlとは無関係で、本番運用では一切使用しない。
    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final String AUTHENTICATED_PATH = "/api/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private RecruitmentApplicationService recruitmentApplicationService;

    @MockitoBean
    private TournamentService tournamentService;

    @Test
    void authenticated対象パスへAuthorizationヘッダーなしでアクセスした場合_401を返す() throws Exception {
        mockMvc.perform(get(AUTHENTICATED_PATH))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).findMe(any());
    }

    @Test
    void authenticated対象パスへ有効なJWTを送信した場合_subjectのuserIdでfindMeが呼ばれ200を返す() throws Exception {
        String token = generateValidToken();

        UserMeResponse response = new UserMeResponse(
                1L, "test-user", java.util.List.of(),
                null, null, null,
                "test@example.com", null, null,
                null, null
        );
        when(userService.findMe(1L)).thenReturn(response);

        mockMvc.perform(get(AUTHENTICATED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test-user"));

        verify(userService, times(1)).findMe(1L);
    }

    @Test
    void authenticated対象パスへ不正署名のJWTを送信した場合_401を返す() throws Exception {
        String token = generateTokenSignedWithDifferentKey();

        mockMvc.perform(get(AUTHENTICATED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).findMe(any());
    }

    @Test
    void authenticated対象パスへ期限切れのJWTを送信した場合_401を返す() throws Exception {
        String token = generateExpiredToken();

        mockMvc.perform(get(AUTHENTICATED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).findMe(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0", "-1", "99999999999999999999"})
    void authenticated対象パスへ署名は正しいがsubjectが不正なJWTを送信した場合_401を返す(String invalidSubject) throws Exception {
        String token = generateTokenWithSubject(invalidSubject);

        mockMvc.perform(get(AUTHENTICATED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).findMe(any());
    }

    @Test
    void authenticated対象パスへ署名は正しいがsubjectクレームが存在しないJWTを送信した場合_401を返す() throws Exception {
        String token = generateTokenWithoutSubject();

        mockMvc.perform(get(AUTHENTICATED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).findMe(any());
    }

    @Test
    void authenticated対象PATCHパスへAuthorizationヘッダーなしでアクセスした場合_401を返す() throws Exception {
        mockMvc.perform(patch(AUTHENTICATED_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateUser(any(), any());
    }

    @Test
    void authenticated対象PATCHパスへ有効なJWTを送信した場合_subjectのuserIdでupdateUserが呼ばれ200を返す() throws Exception {
        String token = generateValidToken();

        UserMeResponse response = new UserMeResponse(
                1L, "test-user", java.util.List.of(),
                null, null, null,
                "test@example.com", null, null,
                null, null
        );
        when(userService.updateUser(eq(1L), any())).thenReturn(response);

        mockMvc.perform(patch(AUTHENTICATED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(userService, times(1)).updateUser(eq(1L), any());
    }

    // ---- /api/teams のSecurityConfig matcher確認 ----
    // このテストクラスは@WebMvcTest(UserController.class)のためTeamControllerを含まないが、
    // Spring SecurityのAuthorizationFilterはController到達前に動作するため、
    // permitAll対象は401にならないこと(404で確認)、authenticated対象は401になることを
    // Controllerの有無に関係なく検証できる(Day 6 Step 1と同じ手法)。

    @Test
    void GET_apiTeamsは認証なしでpermitAllとなる() throws Exception {
        when(teamService.findAllTeams()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk());
    }

    @Test
    void GET_apiTeams数値idは認証なしでpermitAllとなる() throws Exception {
        when(teamService.findTeamById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/teams/1"))
                .andExpect(status().isOk());
    }

    @Test
    void GET_apiTeams数値idmembersは認証なしでpermitAllとなる() throws Exception {
        when(teamService.findTeamMembers(1L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isOk());
    }

    @Test
    void GET_apiTeamsMyは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(get("/api/teams/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void POST_apiTeamsは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // DELETEはGET専用のpermitAll matcher(/api/teams/{id:[0-9]+}/members)に一致しないため、
    // anyRequest().authenticated()の対象になることを確認する。
    @Test
    void DELETE_apiTeams数値idmembers数値idは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(delete("/api/teams/1/members/2"))
                .andExpect(status().isUnauthorized());
    }

    // POST /api/teams/{teamId}/applicationsはpermitAll matcherに一致しないパスのため、
    // anyRequest().authenticated()の対象になることを確認する。
    @Test
    void POST_apiTeams数値idapplicationsは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(post("/api/teams/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /api/teams/{teamId}/applications/{applicationId}/approve|rejectはpermitAll matcherに
    // 一致しないパスのため、anyRequest().authenticated()の対象になることを確認する。
    @Test
    void PATCH_apiTeams数値idapplications数値idapproveは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(patch("/api/teams/1/applications/1/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void PATCH_apiTeams数値idapplications数値idrejectは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(patch("/api/teams/1/applications/1/reject"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/teams/{id:[0-9]+}はパスの末尾がid単独の場合のみ一致するmatcherのため、
    // /api/teams/1/applicationsのような追加セグメントを含むパスには一致しないことを確認する。
    @Test
    void GET_apiTeams数値idapplicationsは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(get("/api/teams/1/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GET_apiApplicationsMeは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(get("/api/applications/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GET_apiTournamentsは認証なしでpermitAllとなる() throws Exception {
        when(tournamentService.findAllTournaments()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/tournaments"))
                .andExpect(status().isOk());
    }

    @Test
    void GET_apiTournaments数値idは認証なしでpermitAllとなる() throws Exception {
        when(tournamentService.findTournamentById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/tournaments/1"))
                .andExpect(status().isOk());
    }

    // POST /api/tournamentsはmatcherが存在しないパスのため、anyRequest().authenticated()の
    // 対象になることを確認する(Controllerに実際のPOSTハンドラーは無いが、認証フィルターは
    // ハンドラー到達前に動作するため、この確認にController側の実装有無は影響しない)。
    @Test
    void POST_apiTournamentsは認証なしでは401を返しpermitAllにならない() throws Exception {
        mockMvc.perform(post("/api/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String generateValidToken() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(3600);

        return encode(jwtEncoder, issuedAt, expiresAt);
    }

    private String generateExpiredToken() {
        Instant issuedAt = Instant.now().minusSeconds(7200).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(3600);

        return encode(jwtEncoder, issuedAt, expiresAt);
    }

    private String generateTokenSignedWithDifferentKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
        keyGenerator.init(256);
        SecretKey differentKey = keyGenerator.generateKey();

        JwtEncoder encoderWithDifferentKey = NimbusJwtEncoder.withSecretKey(differentKey)
                .algorithm(MacAlgorithm.HS256)
                .build();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(3600);

        return encode(encoderWithDifferentKey, issuedAt, expiresAt);
    }

    private String generateTokenWithSubject(String subject) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(3600);

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    private String generateTokenWithoutSubject() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(3600);

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        // .subject(...)を呼ばないことで、sub claim自体が存在しないJWTを生成する。
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    private static String encode(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        return encoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
