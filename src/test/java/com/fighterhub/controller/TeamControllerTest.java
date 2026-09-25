package com.fighterhub.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.config.SecurityConfig;
import com.fighterhub.dto.TeamCreateRequest;
import com.fighterhub.dto.TeamCreateResponse;
import com.fighterhub.dto.TeamMemberResponse;
import com.fighterhub.dto.TeamResponse;
import com.fighterhub.dto.TeamUpdateRequest;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.service.TeamService;

@WebMvcTest(TeamController.class)
@Import(SecurityConfig.class)
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    // SecurityFilterChainがJwtDecoderを要求するためコンテキスト起動に必要。
    // findMy/createTeam関連のテストではBearer Tokenの検証結果としてJwtを返すよう都度スタブする。
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static TeamResponse sampleTeamResponse(Long id, Long tournamentId, Long ownerId) {
        return new TeamResponse(
                id,
                tournamentId,
                "Test Cup",
                ownerId,
                "Owner User",
                "Team Ryu",
                "MASTER",
                List.of(1L, 2L),
                "誰でも歓迎です",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0)
        );
    }

    private static TeamCreateResponse sampleTeamCreateResponse(Long id, Long tournamentId, Long ownerId) {
        return new TeamCreateResponse(
                id,
                tournamentId,
                ownerId,
                "Team Ryu",
                "MASTER",
                List.of(1L, 2L),
                "誰でも歓迎です",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0)
        );
    }

    private static Jwt validJwt(String subject) {
        return Jwt.withTokenValue("valid-jwt-token")
                .header("alg", "HS256")
                .claim("sub", subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void findAllTeams_認証なしで200を返しTeamServiceのfindAllTeamsが呼ばれる() throws Exception {
        when(teamService.findAllTeams()).thenReturn(List.of(sampleTeamResponse(100L, 10L, 1L)));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].tournamentId").value(10))
                .andExpect(jsonPath("$[0].tournamentName").value("Test Cup"))
                .andExpect(jsonPath("$[0].ownerId").value(1))
                .andExpect(jsonPath("$[0].ownerName").value("Owner User"))
                .andExpect(jsonPath("$[0].name").value("Team Ryu"))
                .andExpect(jsonPath("$[0].rankRequirement").value("MASTER"))
                .andExpect(jsonPath("$[0].characterRequirements[0]").value(1))
                .andExpect(jsonPath("$[0].characterRequirements[1]").value(2))
                .andExpect(jsonPath("$[0].recruitmentMessage").value("誰でも歓迎です"));

        verify(teamService, times(1)).findAllTeams();
    }

    @Test
    void findTeamById_認証なしで200を返し指定idでfindTeamByIdが呼ばれる() throws Exception {
        when(teamService.findTeamById(100L)).thenReturn(sampleTeamResponse(100L, 10L, 1L));

        mockMvc.perform(get("/api/teams/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.tournamentName").value("Test Cup"))
                .andExpect(jsonPath("$.ownerName").value("Owner User"));

        verify(teamService, times(1)).findTeamById(100L);
    }

    @Test
    void findMyTeams_JWTありで200を返しJWTのsubjectのuserIdでfindMyTeamsが呼ばれる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(teamService.findMyTeams(1L)).thenReturn(List.of(sampleTeamResponse(100L, 10L, 1L)));

        mockMvc.perform(get("/api/teams/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));

        verify(teamService, times(1)).findMyTeams(1L);
        // /api/teams/my が誤ってfindTeamById({id})側へルーティングされていないことの確認。
        verify(teamService, never()).findTeamById(any());
    }

    @Test
    void findMyTeams_JWTなしで401を返しTeamServiceが呼ばれない() throws Exception {
        mockMvc.perform(get("/api/teams/my"))
                .andExpect(status().isUnauthorized());

        verify(teamService, never()).findMyTeams(any());
        verify(teamService, never()).findTeamById(any());
    }

    @Test
    void apiTeamsMyへのリクエストはfindTeamByIdではなくfindMyTeamsへルーティングされる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(teamService.findMyTeams(1L)).thenReturn(List.of());

        // {id:Long}としてルーティングされていれば"my"をLongへ変換できず400になるはずだが、
        // 実際には200(findMyTeams経由)になることを確認する。
        mockMvc.perform(get("/api/teams/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk());

        verify(teamService, times(1)).findMyTeams(1L);
        verify(teamService, never()).findTeamById(any());
    }

    @Test
    void createTeam_JWTありvalidリクエストの場合_201を返しJWTのsubjectのuserIdでcreateTeamが呼ばれる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(teamService.createTeam(eq(1L), any(TeamCreateRequest.class)))
                .thenReturn(sampleTeamCreateResponse(100L, 10L, 1L));

        String requestBody = """
                {
                  "tournamentId": 10,
                  "name": "Team Ryu",
                  "rankRequirement": "MASTER",
                  "characterRequirements": [1, 2],
                  "recruitmentMessage": "誰でも歓迎です"
                }
                """;

        mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.tournamentId").value(10))
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.name").value("Team Ryu"));

        ArgumentCaptor<TeamCreateRequest> requestCaptor = ArgumentCaptor.forClass(TeamCreateRequest.class);
        verify(teamService, times(1)).createTeam(eq(1L), requestCaptor.capture());
        TeamCreateRequest capturedRequest = requestCaptor.getValue();
        assertEquals(10L, capturedRequest.tournamentId());
        assertEquals("Team Ryu", capturedRequest.name());
    }

    @Test
    void createTeam_JWTなしの場合_401を返しcreateTeamは呼ばれない() throws Exception {
        String requestBody = """
                {
                  "tournamentId": 10,
                  "name": "Team Ryu"
                }
                """;

        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        verify(teamService, never()).createTeam(any(), any());
    }

    @Test
    void createTeam_nameがblankの場合_400を返しcreateTeamは呼ばれない() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);

        String requestBody = """
                {
                  "tournamentId": 10,
                  "name": ""
                }
                """;

        mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(teamService, never()).createTeam(any(), any());
    }

    @Test
    void createTeam_tournamentIdがnullの場合_400を返しcreateTeamは呼ばれない() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);

        String requestBody = """
                {
                  "name": "Team Ryu"
                }
                """;

        mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(teamService, never()).createTeam(any(), any());
    }

    @Test
    void findTeamMembers_認証なしで200を返し指定idでfindTeamMembersが呼ばれる() throws Exception {
        TeamMemberResponse member = new TeamMemberResponse(
                1L, "Owner User", LocalDateTime.of(2026, 1, 1, 0, 0));
        when(teamService.findTeamMembers(1L)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].userName").value("Owner User"))
                .andExpect(jsonPath("$[0].joinedAt").exists());

        verify(teamService, times(1)).findTeamMembers(1L);
    }

    @Test
    void findTeamMembers_メンバー0人の場合_200と空Listを返す() throws Exception {
        when(teamService.findTeamMembers(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(teamService, times(1)).findTeamMembers(1L);
    }

    @Test
    void findTeamMembers_TeamServiceがTeamNotFoundExceptionを投げた場合_404を返す() throws Exception {
        when(teamService.findTeamMembers(999L)).thenThrow(new TeamNotFoundException(999L));

        mockMvc.perform(get("/api/teams/999/members"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Team not found. id=999"))
                .andExpect(jsonPath("$.path").value("/api/teams/999/members"));
    }

    @Test
    void updateTeam_JWTありvalidリクエストの場合_200を返しJWTのsubjectのuserIdとPathのidでupdateTeamが呼ばれる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(teamService.updateTeam(eq(1L), eq(100L), any(TeamUpdateRequest.class)))
                .thenReturn(sampleTeamResponse(100L, 10L, 1L));

        String requestBody = """
                {
                  "name": "Team Ryu"
                }
                """;

        mockMvc.perform(patch("/api/teams/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("Team Ryu"));

        verify(teamService, times(1)).updateTeam(eq(1L), eq(100L), any(TeamUpdateRequest.class));
    }

    @Test
    void updateTeam_JWTなしの場合_401を返しupdateTeamは呼ばれない() throws Exception {
        mockMvc.perform(patch("/api/teams/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(teamService, never()).updateTeam(any(), any(), any());
    }

    @Test
    void updateTeam_nameが空文字の場合_400を返しupdateTeamは呼ばれない() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);

        String requestBody = """
                {
                  "name": ""
                }
                """;

        mockMvc.perform(patch("/api/teams/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(teamService, never()).updateTeam(any(), any(), any());
    }

    @Test
    void updateTeam_明示的nullと項目なしが正しくTeamUpdateRequestへ渡る() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(teamService.updateTeam(eq(1L), eq(100L), any(TeamUpdateRequest.class)))
                .thenReturn(sampleTeamResponse(100L, 10L, 1L));

        // name/rankRequirement/characterRequirementsは項目なし(undefined)、
        // recruitmentMessageのみ明示的null。
        String requestBody = """
                {
                  "recruitmentMessage": null
                }
                """;

        mockMvc.perform(patch("/api/teams/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<TeamUpdateRequest> requestCaptor = ArgumentCaptor.forClass(TeamUpdateRequest.class);
        verify(teamService, times(1)).updateTeam(eq(1L), eq(100L), requestCaptor.capture());
        TeamUpdateRequest capturedRequest = requestCaptor.getValue();

        assertTrue(capturedRequest.name().isUndefined());
        assertTrue(capturedRequest.rankRequirement().isUndefined());
        assertTrue(capturedRequest.characterRequirements().isUndefined());
        assertFalse(capturedRequest.recruitmentMessage().isUndefined());
        assertTrue(capturedRequest.recruitmentMessage().isPresent());
        assertEquals(null, capturedRequest.recruitmentMessage().get());
    }
}
