package com.fighterhub.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.config.SecurityConfig;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.exception.EmailAlreadyExistsException;
import com.fighterhub.service.UserService;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_未認証で正常なリクエストを送信した場合_201とUserCreateResponseを返す() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123",
                  "name": "Test User",
                  "characters": [
                    {
                      "characterId": 1,
                      "rank": "MASTER",
                      "mr": 1600
                    }
                  ],
                  "playTimeStart": "20:00",
                  "playTimeEnd": "23:30",
                  "message": "よろしくお願いします"
                }
                """;

        when(userService.createUser(any(UserCreateRequest.class)))
                .thenReturn(new UserCreateResponse(1L));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));

        ArgumentCaptor<UserCreateRequest> requestCaptor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService, times(1)).createUser(requestCaptor.capture());

        UserCreateRequest capturedRequest = requestCaptor.getValue();
        assertEquals("test@example.com", capturedRequest.email());
        assertEquals("Test User", capturedRequest.name());
        assertEquals(1, capturedRequest.characters().size());
        assertEquals(1L, capturedRequest.characters().get(0).characterId());
        assertEquals("MASTER", capturedRequest.characters().get(0).rank());
        assertEquals(1600, capturedRequest.characters().get(0).mr());
        assertEquals(LocalTime.of(20, 0), capturedRequest.playTimeStart());
        assertEquals(LocalTime.of(23, 30), capturedRequest.playTimeEnd());
        assertEquals("よろしくお願いします", capturedRequest.message());
    }

    @Test
    void createUser_email形式が不正な場合_400を返す() throws Exception {

        String requestBody = """
                {
                  "email": "invalid-email",
                  "password": "password123",
                  "name": "Test User",
                  "characters": [
                    {
                      "characterId": 1,
                      "rank": "MASTER",
                      "mr": 1600
                    }
                  ],
                  "playTimeStart": "20:00",
                  "playTimeEnd": "23:30",
                  "message": "よろしくお願いします"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.message", containsString("email")));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_passwordが8文字未満の場合_400を返す() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "1234567",
                  "name": "Test User",
                  "characters": [
                    {
                      "characterId": 1,
                      "rank": "MASTER",
                      "mr": 1600
                    }
                  ],
                  "playTimeStart": "20:00",
                  "playTimeEnd": "23:30",
                  "message": "よろしくお願いします"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("password")));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_charactersが指定されていない場合_400を返す() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123",
                  "name": "Test User",
                  "playTimeStart": "20:00",
                  "playTimeEnd": "23:30",
                  "message": "よろしくお願いします"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("characters")));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_emailが既に登録されている場合_409を返す() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123",
                  "name": "Test User",
                  "characters": [
                    {
                      "characterId": 1,
                      "rank": "MASTER",
                      "mr": 1600
                    }
                  ],
                  "playTimeStart": "20:00",
                  "playTimeEnd": "23:30",
                  "message": "よろしくお願いします"
                }
                """;

        when(userService.createUser(any(UserCreateRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("test@example.com"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.message").value("Email already exists. email=test@example.com"));
    }

    @Test
    void createUser_playTimeStartが時刻として解釈できない場合_400を返す() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123",
                  "name": "Test User",
                  "characters": [
                    {
                      "characterId": 1,
                      "rank": "MASTER",
                      "mr": 1600
                    }
                  ],
                  "playTimeStart": "not-a-time",
                  "playTimeEnd": "23:30",
                  "message": "よろしくお願いします"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request."))
                .andExpect(jsonPath("$.path").value("/api/users"));

        verify(userService, never()).createUser(any());
    }
}
