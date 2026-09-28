package com.fighterhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.fighterhub.dto.TournamentCreateRequest;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.exception.NotAdminException;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.service.TournamentService;

@WebMvcTest(TournamentController.class)
@Import(SecurityConfig.class)
class TournamentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentService tournamentService;

    // SecurityFilterChainがJwtDecoderを要求するためコンテキスト起動に必要。
    // permitAllのため、このクラスのテストではdecodeは呼ばれない。
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

    private static TournamentResponse sampleResponse(Long id) {
        return new TournamentResponse(
                id,
                "STREET FIGHTER 6 CUP",
                3,
                LocalDateTime.of(2026, 10, 1, 19, 0),
                64,
                "OPEN",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0)
        );
    }

    @Test
    void findAllTournaments_認証なしで200を返し一覧を取得できる() throws Exception {
        when(tournamentService.findAllTournaments()).thenReturn(List.of(sampleResponse(1L)));

        mockMvc.perform(get("/api/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("STREET FIGHTER 6 CUP"))
                .andExpect(jsonPath("$[0].teamSize").value(3))
                .andExpect(jsonPath("$[0].maxPlayers").value(64))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void findTournamentById_認証なしで200を返し指定idで取得できる() throws Exception {
        when(tournamentService.findTournamentById(1L)).thenReturn(sampleResponse(1L));

        mockMvc.perform(get("/api/tournaments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("STREET FIGHTER 6 CUP"));
    }

    @Test
    void findTournamentById_TournamentServiceがTournamentNotFoundExceptionを投げた場合_404を返す() throws Exception {
        when(tournamentService.findTournamentById(999L)).thenThrow(new TournamentNotFoundException(999L));

        mockMvc.perform(get("/api/tournaments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Tournament not found. id=999"))
                .andExpect(jsonPath("$.path").value("/api/tournaments/999"));
    }

    @Test
    void createTournament_JWTありvalidリクエストの場合_201を返しJWTのsubjectのuserIdでcreateTournamentが呼ばれる() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(tournamentService.createTournament(eq(1L), any(TournamentCreateRequest.class)))
                .thenReturn(sampleResponse(100L));

        String requestBody = """
                {
                  "name": "STREET FIGHTER 6 CUP",
                  "teamSize": 3,
                  "startAt": "2026-10-01T19:00:00",
                  "maxPlayers": 64,
                  "status": "OPEN"
                }
                """;

        mockMvc.perform(post("/api/tournaments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("STREET FIGHTER 6 CUP"));

        verify(tournamentService, times(1))
                .createTournament(eq(1L), any(TournamentCreateRequest.class));
    }

    @Test
    void createTournament_JWTなしの場合_401を返しcreateTournamentは呼ばれない() throws Exception {
        String requestBody = """
                {
                  "name": "STREET FIGHTER 6 CUP",
                  "teamSize": 3,
                  "startAt": "2026-10-01T19:00:00",
                  "maxPlayers": 64,
                  "status": "OPEN"
                }
                """;

        mockMvc.perform(post("/api/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        verify(tournamentService, never()).createTournament(any(), any());
    }

    @Test
    void createTournament_ServiceがNotAdminExceptionを投げた場合_403を返す() throws Exception {
        Jwt jwt = validJwt("2");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(tournamentService.createTournament(eq(2L), any(TournamentCreateRequest.class)))
                .thenThrow(new NotAdminException(2L));

        String requestBody = """
                {
                  "name": "STREET FIGHTER 6 CUP",
                  "teamSize": 3,
                  "startAt": "2026-10-01T19:00:00",
                  "maxPlayers": 64,
                  "status": "OPEN"
                }
                """;

        mockMvc.perform(post("/api/tournaments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTournament_ServiceがUserNotFoundExceptionを投げた場合_404を返す() throws Exception {
        Jwt jwt = validJwt("999");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);
        when(tournamentService.createTournament(eq(999L), any(TournamentCreateRequest.class)))
                .thenThrow(new UserNotFoundException(999L));

        String requestBody = """
                {
                  "name": "STREET FIGHTER 6 CUP",
                  "teamSize": 3,
                  "startAt": "2026-10-01T19:00:00",
                  "maxPlayers": 64,
                  "status": "OPEN"
                }
                """;

        mockMvc.perform(post("/api/tournaments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTournament_nameが空文字の場合_400を返しcreateTournamentは呼ばれない() throws Exception {
        Jwt jwt = validJwt("1");
        when(jwtDecoder.decode("valid-jwt-token")).thenReturn(jwt);

        String requestBody = """
                {
                  "name": "",
                  "teamSize": 3,
                  "startAt": "2026-10-01T19:00:00",
                  "maxPlayers": 64,
                  "status": "OPEN"
                }
                """;

        mockMvc.perform(post("/api/tournaments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(tournamentService, never()).createTournament(any(), any());
    }
}
