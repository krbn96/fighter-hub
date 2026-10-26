package com.fighterhub.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import com.fighterhub.dto.TournamentCreateRequest;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.service.TournamentService;
import com.fighterhub.service.UserService;

// 「大会作成→チーム作成→別ユーザーの参加申請→オーナー承認→メンバー一覧確認」という
// 一連の業務フローを、実際のHTTP API(Controller〜Service〜Repository〜実PostgreSQL)を通して
// 検証する結合テスト。
//
// 既存の個別テスト(TeamControllerTest/RecruitmentApplicationControllerTest等)はService層を
// Mockitoでモック化したController単体テストであり、「複数のHTTPリクエストをまたいだ状態変化」
// (応募→承認で実際にTeamMemberが作成され、別リクエストのGET /membersで見えるようになる等)は
// 検証できない。このテストはその隙間を埋める目的で追加する(個別テストと同じ観点の重複検証は
// 避け、複数Controller・複数リクエストをまたぐ一連の流れにのみ焦点を当てる)。
//
// Tournament作成・User作成はSetup(前提条件の用意)としてServiceを直接呼び出す
// (UserMePatchPersistenceTestと同じ方針。ADMINへの昇格はAPI経由で提供されていないため、
// 本テストでもJdbcTemplateでroleを直接更新する)。Team作成・参加申請・承認・一覧取得という
// 「このテストで検証したい一連の流れ」はすべて実際のHTTP経由(MockMvc)で行う。
//
// 各テストは専用のTournament/Team/Userをそれぞれ新規作成し、既存データや他のテストの
// 実行結果に依存しない(@AfterEachで作成した行をすべて削除する)。
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.secret=" + TeamApplicationApprovalFlowIntegrationTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class TeamApplicationApprovalFlowIntegrationTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdTournamentIds = new ArrayList<>();
    private final List<Long> createdTeamIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // FK制約(RESTRICT、ON DELETE CASCADE無し)の都合上、
        // 参加申請・TeamMember(teamに依存)→Team→Tournament→Userの順で削除する。
        for (Long teamId : createdTeamIds) {
            jdbcTemplate.update("DELETE FROM t_recruitment_application WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM t_team_members WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM t_teams WHERE id = ?", teamId);
        }
        for (Long tournamentId : createdTournamentIds) {
            jdbcTemplate.update("DELETE FROM t_tournaments WHERE id = ?", tournamentId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM t_users WHERE id = ?", userId);
        }
    }

    @Test
    void 正常系_大会作成からチーム作成_応募_承認_メンバー一覧確認までが実際のHTTPフローで成功する() throws Exception {
        Long characterId = anyExistingCharacterId();

        Long adminId = createAdminUser(characterId);
        Long tournamentId = createTournament(adminId, 3, 64);

        Long ownerId = createUser("owner", characterId);
        String ownerToken = generateToken(ownerId);

        Long teamId = createTeamViaHttp(ownerToken, tournamentId, "Team Ryu");

        Long applicantId = createUser("applicant", characterId);
        String applicantToken = generateToken(applicantId);

        MvcResult applyResult = mockMvc.perform(post("/api/teams/" + teamId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"よろしくお願いします\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        Long applicationId = ((Number) JsonPath.read(
                applyResult.getResponse().getContentAsString(), "$.id")).longValue();

        // オーナーが自身のチームへの参加申請一覧を取得すると、PENDING状態の申請が見える。
        mockMvc.perform(get("/api/teams/" + teamId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(applicationId))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        // オーナーが承認すると、TeamMemberが作成されApplicationがAPPROVEDになる。
        mockMvc.perform(patch(
                        "/api/teams/" + teamId + "/applications/" + applicationId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 承認は別のHTTPリクエスト(PATCH)で行われたが、GET /membersという別のリクエストでも
        // 新しいメンバーが見えること(=実際にDBへ永続化されていること)を確認する。
        // GET /api/teams/{id}/membersはpermitAllのためAuthorizationヘッダーは不要。
        mockMvc.perform(get("/api/teams/" + teamId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].userId").value(org.hamcrest.Matchers.containsInAnyOrder(
                        ownerId.intValue(), applicantId.intValue())));

        // 応募者自身の一覧からも、承認された申請がAPPROVEDとして見える。
        mockMvc.perform(get("/api/applications/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void 定員到達後は既存PENDING申請の承認も409になる_新規応募自体は201のまま() throws Exception {
        Long characterId = anyExistingCharacterId();

        Long adminId = createAdminUser(characterId);
        // teamSize=2: owner 1名 + 承認1名で定員に達する。
        Long tournamentId = createTournament(adminId, 2, 64);

        Long ownerId = createUser("owner", characterId);
        String ownerToken = generateToken(ownerId);
        Long teamId = createTeamViaHttp(ownerToken, tournamentId, "Team Full");

        Long applicantCId = createUser("applicant-c", characterId);
        String tokenC = generateToken(applicantCId);
        Long applicationCId = applyViaHttp(tokenC, teamId, "C応募");

        Long applicantDId = createUser("applicant-d", characterId);
        String tokenD = generateToken(applicantDId);
        Long applicationDId = applyViaHttp(tokenD, teamId, "D応募(先に出しておく)");

        // Cを承認 → owner+C = 2名でteamSize(2)に到達し、定員に達する。
        mockMvc.perform(patch(
                        "/api/teams/" + teamId + "/applications/" + applicationCId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Dの申請は定員到達前に作成済み(既存PENDING申請)だが、承認時点では定員到達後のため
        // 409(TeamFullException)になる。既存のAPI仕様どおりの挙動であることを確認する。
        mockMvc.perform(patch(
                        "/api/teams/" + teamId + "/applications/" + applicationDId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // 409になってもDの申請自体はPENDINGのまま(拒否や承認済みへ変化しない)。
        mockMvc.perform(get("/api/applications/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        // 定員到達後でも、新規の参加申請の作成(POST)自体は現在の仕様どおり201で成功する
        // (createApplication自体は定員チェックを行わず、承認時にのみチェックする仕様)。
        Long applicantEId = createUser("applicant-e", characterId);
        String tokenE = generateToken(applicantEId);
        mockMvc.perform(post("/api/teams/" + teamId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 論理削除されたメンバーは一覧に表示されず定員判定の実人数からも除外される() throws Exception {
        Long characterId = anyExistingCharacterId();

        Long adminId = createAdminUser(characterId);
        Long tournamentId = createTournament(adminId, 2, 64);

        Long ownerId = createUser("owner", characterId);
        String ownerToken = generateToken(ownerId);
        Long teamId = createTeamViaHttp(ownerToken, tournamentId, "Team SoftDelete");

        Long applicantEId = createUser("applicant-e", characterId);
        String tokenE = generateToken(applicantEId);
        Long applicationEId = applyViaHttp(tokenE, teamId, null);

        mockMvc.perform(patch(
                        "/api/teams/" + teamId + "/applications/" + applicationEId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // owner+E = 2名でteamSize(2)に到達(定員に達した状態)。
        mockMvc.perform(get("/api/teams/" + teamId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Eを論理削除する(削除APIは存在しないため、既存テスト(UserRepositoryTest等)と
        // 同じ方法でJdbcTemplateから直接delete_flagを更新する)。
        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", applicantEId);

        // 論理削除されたEはメンバー一覧(有効なメンバーのみ)に表示されなくなる。
        mockMvc.perform(get("/api/teams/" + teamId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(ownerId.intValue()));

        // 定員判定も「有効なメンバー数」(実人数)ベースのため、Eの論理削除により
        // 実質1名(owner)まで空きが生まれ、新規の承認が再び成功するようになる。
        Long applicantFId = createUser("applicant-f", characterId);
        String tokenF = generateToken(applicantFId);
        Long applicationFId = applyViaHttp(tokenF, teamId, null);

        mockMvc.perform(patch(
                        "/api/teams/" + teamId + "/applications/" + applicationFId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/teams/" + teamId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].userId").value(org.hamcrest.Matchers.containsInAnyOrder(
                        ownerId.intValue(), applicantFId.intValue())));
    }

    @Test
    void 未認証の場合チーム作成_応募_承認のいずれも401になる() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tournamentId\": 1, \"name\": \"No Auth Team\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/teams/999999/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": null}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/teams/999999/applications/999999/approve"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/teams/999999/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void チームオーナーでない別チームのオーナーが操作すると403になる() throws Exception {
        Long characterId = anyExistingCharacterId();
        Long adminId = createAdminUser(characterId);

        Long tournament1Id = createTournament(adminId, 5, 64);
        Long owner1Id = createUser("owner1", characterId);
        String owner1Token = generateToken(owner1Id);
        Long team1Id = createTeamViaHttp(owner1Token, tournament1Id, "Team One");

        Long applicantId = createUser("applicant", characterId);
        String applicantToken = generateToken(applicantId);
        Long applicationId = applyViaHttp(applicantToken, team1Id, null);

        // 無関係な別Tournament・別Teamのオーナー。
        Long tournament2Id = createTournament(adminId, 5, 64);
        Long owner2Id = createUser("owner2", characterId);
        String owner2Token = generateToken(owner2Id);
        createTeamViaHttp(owner2Token, tournament2Id, "Team Two");

        mockMvc.perform(get("/api/teams/" + team1Id + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner2Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(
                        "/api/teams/" + team1Id + "/applications/" + applicationId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void 存在しないTeamやApplicationを指定した場合は404になる() throws Exception {
        Long characterId = anyExistingCharacterId();
        Long adminId = createAdminUser(characterId);
        Long tournamentId = createTournament(adminId, 5, 64);

        Long ownerId = createUser("owner", characterId);
        String ownerToken = generateToken(ownerId);
        Long teamId = createTeamViaHttp(ownerToken, tournamentId, "Team Exists");

        // 存在しないTeamへの操作。
        mockMvc.perform(get("/api/teams/999999999/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/teams/999999999/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": null}"))
                .andExpect(status().isNotFound());

        // 存在するTeamだが、存在しないApplicationへの承認。
        mockMvc.perform(patch(
                        "/api/teams/" + teamId + "/applications/999999999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // ==== Setup helpers ====

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        Assumptions.assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないためスキップする");
        return characters.get(0).getId();
    }

    private Long createUser(String label, Long characterId) {
        UserCreateRequest request = new UserCreateRequest(
                "team-flow-" + label + "-" + UUID.randomUUID() + "@example.com",
                "password123",
                label + " User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                null
        );
        UserCreateResponse created = userService.createUser(request);
        createdUserIds.add(created.id());
        return created.id();
    }

    // ADMINへの昇格はAPI経由では提供されていない(DB直接更新を前提とした運用のため)。
    // 既存のUserRepositoryTest等と同じ方針で、テスト内のみJdbcTemplateで直接更新する。
    private Long createAdminUser(Long characterId) {
        Long userId = createUser("admin", characterId);
        jdbcTemplate.update("UPDATE t_users SET role = 'ADMIN' WHERE id = ?", userId);
        return userId;
    }

    private Long createTournament(Long adminId, int teamSize, int maxPlayers) {
        LocalDateTime now = LocalDateTime.now();
        TournamentCreateRequest request = new TournamentCreateRequest(
                "Integration Test Cup " + UUID.randomUUID(),
                teamSize,
                now.plusDays(2),
                now.plusDays(1),
                maxPlayers
        );
        TournamentResponse response = tournamentService.createTournament(adminId, request);
        createdTournamentIds.add(response.id());
        return response.id();
    }

    private Long createTeamViaHttp(String ownerToken, Long tournamentId, String teamName) throws Exception {
        String body = """
                {"tournamentId": %d, "name": "%s", "rankRequirement": null,
                 "characterRequirements": null, "recruitmentMessage": null}
                """.formatted(tournamentId, teamName);

        MvcResult result = mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Long teamId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
        createdTeamIds.add(teamId);
        return teamId;
    }

    private Long applyViaHttp(String applicantToken, Long teamId, String message) throws Exception {
        String messageJson = message == null ? "null" : "\"" + message + "\"";
        String body = "{\"message\": " + messageJson + "}";

        MvcResult result = mockMvc.perform(post("/api/teams/" + teamId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
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
