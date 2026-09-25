package com.fighterhub.controller;

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
import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.exception.ApplicationAlreadyProcessedException;
import com.fighterhub.exception.CannotApplyToOwnTeamException;
import com.fighterhub.exception.DuplicatePendingApplicationException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.RecruitmentApplicationNotFoundException;
import com.fighterhub.exception.TeamFullException;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.service.RecruitmentApplicationService;

@WebMvcTest(RecruitmentApplicationController.class)
@Import(SecurityConfig.class)
class RecruitmentApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecruitmentApplicationService recruitmentApplicationService;

    // SecurityFilterChainがJwtDecoderを要求するためコンテキスト起動に必要。
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static Jwt validJwt(String subject) {
        return Jwt.withTokenValue("valid-jwt-token")
                .header("alg", "HS256")
                .claim("sub", subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private static RecruitmentApplicationResponse sampleResponse(String message) {
        return sampleResponse(message, RecruitmentApplicationStatus.PENDING);
    }

    private static RecruitmentApplicationResponse sampleResponse(String message, RecruitmentApplicationStatus status) {
        return new RecruitmentApplicationResponse(
                500L,
                100L,
                2L,
                "Applicant User",
                message,
                status,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }

    @Test
    void createApplication_JWTありvalidリクエストの場合_201を返しJWTのsubjectのuserIdとPathのteamIdでcreateApplicationが呼ばれる() throws Exception {
        Jwt jwt = validJwt("2");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.createApplication(
                eq(2L), eq(100L), any(RecruitmentApplicationCreateRequest.class)))
                .thenReturn(sampleResponse("よろしくお願いします"));

        String requestBody = """
                {
                  "message": "よろしくお願いします"
                }
                """;

        mockMvc.perform(post("/api/teams/100/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.teamId").value(100))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.userName").value("Applicant User"))
                .andExpect(jsonPath("$.message").value("よろしくお願いします"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(recruitmentApplicationService, times(1))
                .createApplication(eq(2L), eq(100L), any(RecruitmentApplicationCreateRequest.class));
    }

    @Test
    void createApplication_JWTなしの場合_401を返しcreateApplicationは呼ばれない() throws Exception {
        String requestBody = """
                {
                  "message": "よろしくお願いします"
                }
                """;

        mockMvc.perform(post("/api/teams/100/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        verify(recruitmentApplicationService, never()).createApplication(any(), any(), any());
    }

    @Test
    void createApplication_ServiceがCannotApplyToOwnTeamExceptionを投げた場合_400を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.createApplication(eq(1L), eq(100L), any(RecruitmentApplicationCreateRequest.class)))
                .thenThrow(new CannotApplyToOwnTeamException(1L, 100L));

        mockMvc.perform(post("/api/teams/100/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createApplication_ServiceがTeamNotFoundExceptionを投げた場合_404を返す() throws Exception {
        Jwt jwt = validJwt("2");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.createApplication(eq(2L), eq(999L), any(RecruitmentApplicationCreateRequest.class)))
                .thenThrow(new TeamNotFoundException(999L));

        mockMvc.perform(post("/api/teams/999/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createApplication_ServiceがDuplicateTournamentMembershipExceptionを投げた場合_409を返す() throws Exception {
        Jwt jwt = validJwt("2");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.createApplication(eq(2L), eq(100L), any(RecruitmentApplicationCreateRequest.class)))
                .thenThrow(new DuplicateTournamentMembershipException(2L, 10L));

        mockMvc.perform(post("/api/teams/100/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createApplication_ServiceがDuplicatePendingApplicationExceptionを投げた場合_409を返す() throws Exception {
        Jwt jwt = validJwt("2");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.createApplication(eq(2L), eq(100L), any(RecruitmentApplicationCreateRequest.class)))
                .thenThrow(new DuplicatePendingApplicationException(100L, 2L));

        mockMvc.perform(post("/api/teams/100/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void findTeamApplications_ownerJWTありの場合_200を返しJWTのsubjectとPathのteamIdでfindTeamApplicationsが呼ばれる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.findTeamApplications(1L, 100L))
                .thenReturn(List.of(sampleResponse("よろしくお願いします")));

        mockMvc.perform(get("/api/teams/100/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(500))
                .andExpect(jsonPath("$[0].teamId").value(100))
                .andExpect(jsonPath("$[0].userId").value(2))
                .andExpect(jsonPath("$[0].message").value("よろしくお願いします"));

        verify(recruitmentApplicationService, times(1)).findTeamApplications(1L, 100L);
    }

    @Test
    void findTeamApplications_申請が0件の場合_200と空配列を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.findTeamApplications(1L, 100L)).thenReturn(List.of());

        mockMvc.perform(get("/api/teams/100/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findTeamApplications_JWTなしの場合_401を返しfindTeamApplicationsは呼ばれない() throws Exception {
        mockMvc.perform(get("/api/teams/100/applications"))
                .andExpect(status().isUnauthorized());

        verify(recruitmentApplicationService, never()).findTeamApplications(any(), any());
    }

    @Test
    void findTeamApplications_ServiceがNotTeamOwnerExceptionを投げた場合_403を返す() throws Exception {
        Jwt jwt = validJwt("999");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.findTeamApplications(999L, 100L))
                .thenThrow(new NotTeamOwnerException(999L, 100L));

        mockMvc.perform(get("/api/teams/100/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findTeamApplications_ServiceがTeamNotFoundExceptionを投げた場合_404を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.findTeamApplications(1L, 999L))
                .thenThrow(new TeamNotFoundException(999L));

        mockMvc.perform(get("/api/teams/999/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveApplication_ownerJWTありの場合_200を返しJWTのsubjectとPathのteamId_applicationIdでapproveApplicationが呼ばれる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.approveApplication(1L, 100L, 500L))
                .thenReturn(sampleResponse("よろしくお願いします", RecruitmentApplicationStatus.APPROVED));

        mockMvc.perform(patch("/api/teams/100/applications/500/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(recruitmentApplicationService, times(1)).approveApplication(1L, 100L, 500L);
    }

    @Test
    void approveApplication_JWTなしの場合_401を返しapproveApplicationは呼ばれない() throws Exception {
        mockMvc.perform(patch("/api/teams/100/applications/500/approve"))
                .andExpect(status().isUnauthorized());

        verify(recruitmentApplicationService, never()).approveApplication(any(), any(), any());
    }

    @Test
    void approveApplication_ServiceがNotTeamOwnerExceptionを投げた場合_403を返す() throws Exception {
        Jwt jwt = validJwt("999");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.approveApplication(999L, 100L, 500L))
                .thenThrow(new NotTeamOwnerException(999L, 100L));

        mockMvc.perform(patch("/api/teams/100/applications/500/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveApplication_ServiceがTeamNotFoundExceptionを投げた場合_404を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.approveApplication(1L, 999L, 500L))
                .thenThrow(new TeamNotFoundException(999L));

        mockMvc.perform(patch("/api/teams/999/applications/500/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveApplication_ServiceがRecruitmentApplicationNotFoundExceptionを投げた場合_404を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.approveApplication(1L, 100L, 999L))
                .thenThrow(new RecruitmentApplicationNotFoundException(100L, 999L));

        mockMvc.perform(patch("/api/teams/100/applications/999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveApplication_ServiceがApplicationAlreadyProcessedExceptionを投げた場合_409を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.approveApplication(1L, 100L, 500L))
                .thenThrow(new ApplicationAlreadyProcessedException(500L));

        mockMvc.perform(patch("/api/teams/100/applications/500/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isConflict());
    }

    @Test
    void approveApplication_ServiceがDuplicateTournamentMembershipExceptionを投げた場合_409を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.approveApplication(1L, 100L, 500L))
                .thenThrow(new DuplicateTournamentMembershipException(2L, 10L));

        mockMvc.perform(patch("/api/teams/100/applications/500/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isConflict());
    }

    @Test
    void approveApplication_ServiceがTeamFullExceptionを投げた場合_409を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.approveApplication(1L, 100L, 500L))
                .thenThrow(new TeamFullException(100L));

        mockMvc.perform(patch("/api/teams/100/applications/500/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectApplication_ownerJWTありの場合_200を返しResponseStatusがREJECTEDになる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.rejectApplication(1L, 100L, 500L))
                .thenReturn(sampleResponse("よろしくお願いします", RecruitmentApplicationStatus.REJECTED));

        mockMvc.perform(patch("/api/teams/100/applications/500/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(recruitmentApplicationService, times(1)).rejectApplication(1L, 100L, 500L);
    }

    @Test
    void rejectApplication_JWTなしの場合_401を返しrejectApplicationは呼ばれない() throws Exception {
        mockMvc.perform(patch("/api/teams/100/applications/500/reject"))
                .andExpect(status().isUnauthorized());

        verify(recruitmentApplicationService, never()).rejectApplication(any(), any(), any());
    }

    @Test
    void rejectApplication_ServiceがNotTeamOwnerExceptionを投げた場合_403を返す() throws Exception {
        Jwt jwt = validJwt("999");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.rejectApplication(999L, 100L, 500L))
                .thenThrow(new NotTeamOwnerException(999L, 100L));

        mockMvc.perform(patch("/api/teams/100/applications/500/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectApplication_ServiceがTeamNotFoundExceptionを投げた場合_404を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.rejectApplication(1L, 999L, 500L))
                .thenThrow(new TeamNotFoundException(999L));

        mockMvc.perform(patch("/api/teams/999/applications/500/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectApplication_ServiceがRecruitmentApplicationNotFoundExceptionを投げた場合_404を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.rejectApplication(1L, 100L, 999L))
                .thenThrow(new RecruitmentApplicationNotFoundException(100L, 999L));

        mockMvc.perform(patch("/api/teams/100/applications/999/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectApplication_ServiceがApplicationAlreadyProcessedExceptionを投げた場合_409を返す() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.rejectApplication(1L, 100L, 500L))
                .thenThrow(new ApplicationAlreadyProcessedException(500L));

        mockMvc.perform(patch("/api/teams/100/applications/500/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isConflict());
    }
}
