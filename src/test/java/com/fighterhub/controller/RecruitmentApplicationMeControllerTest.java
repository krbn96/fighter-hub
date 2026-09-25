package com.fighterhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.config.SecurityConfig;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.service.RecruitmentApplicationService;

@WebMvcTest(RecruitmentApplicationMeController.class)
@Import(SecurityConfig.class)
class RecruitmentApplicationMeControllerTest {

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

    private static RecruitmentApplicationResponse sampleResponse() {
        return new RecruitmentApplicationResponse(
                500L,
                100L,
                2L,
                "Applicant User",
                "よろしくお願いします",
                RecruitmentApplicationStatus.PENDING,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }

    @Test
    void findMyApplications_JWTありの場合_200を返しJWTのsubjectのuserIdでfindMyApplicationsが呼ばれる() throws Exception {
        Jwt jwt = validJwt("2");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.findMyApplications(2L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/applications/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(500))
                .andExpect(jsonPath("$[0].teamId").value(100))
                .andExpect(jsonPath("$[0].userId").value(2));

        verify(recruitmentApplicationService, times(1)).findMyApplications(2L);
    }

    @Test
    void findMyApplications_申請が0件の場合_200と空配列を返す() throws Exception {
        Jwt jwt = validJwt("2");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(recruitmentApplicationService.findMyApplications(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/applications/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findMyApplications_JWTなしの場合_401を返しfindMyApplicationsは呼ばれない() throws Exception {
        mockMvc.perform(get("/api/applications/me"))
                .andExpect(status().isUnauthorized());

        verify(recruitmentApplicationService, never()).findMyApplications(any());
    }
}
