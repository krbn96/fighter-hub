package com.fighterhub.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import com.fighterhub.dto.CharacterResponse;
import com.fighterhub.service.CharacterService;
import com.fighterhub.config.SecurityConfig;
import com.fighterhub.exception.CharacterNotFoundException;

@WebMvcTest(CharacterController.class)
@Import(SecurityConfig.class)
class CharacterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CharacterService characterService;

    @Test
    void findById_存在するIDを指定した場合_200とキャラクターを返す() throws Exception {

        CharacterResponse response = new CharacterResponse(1L, "Ryu");

        when(characterService.findById(1L))
                .thenReturn(response);

        mockMvc.perform(get("/api/characters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ryu"));
    }

    @Test
    void findById_存在しないIDを指定した場合_404を返す() throws Exception {

        when(characterService.findById(999L))
                .thenThrow(new CharacterNotFoundException(999L));

        mockMvc.perform(get("/api/characters/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Character not found. id=999"))
                .andExpect(jsonPath("$.path").value("/api/characters/999"));
    }

    @Test
    void findAll_キャラクターが存在する場合_200と一覧を返す() throws Exception {

        List<CharacterResponse> responses = List.of(
                new CharacterResponse(1L, "Ryu"),
                new CharacterResponse(2L, "Ken")
        );

        when(characterService.findAll())
                .thenReturn(responses);

        mockMvc.perform(get("/api/characters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Ryu"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Ken"));
    }
}