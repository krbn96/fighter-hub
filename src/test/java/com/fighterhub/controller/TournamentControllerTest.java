package com.fighterhub.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.config.SecurityConfig;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.exception.TournamentNotFoundException;
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
}
