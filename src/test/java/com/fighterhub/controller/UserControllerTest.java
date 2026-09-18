package com.fighterhub.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.config.SecurityConfig;
import com.fighterhub.dto.UserCharacterResponse;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.dto.UserPublicResponse;
import com.fighterhub.exception.EmailAlreadyExistsException;
import com.fighterhub.exception.UserNotFoundException;
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

    @Test
    void findById_未認証で存在するuserIdを指定した場合_200とUserPublicResponseを返す() throws Exception {

        UserCharacterResponse characterResponse = new UserCharacterResponse(1L, "MASTER", 1600);

        UserPublicResponse response = new UserPublicResponse(
                1L,
                "Test User",
                List.of(characterResponse),
                LocalTime.of(20, 0),
                LocalTime.of(23, 30),
                "よろしくお願いします",
                "testxid",
                "test#1234",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0)
        );

        when(userService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.characters[0].characterId").value(1))
                .andExpect(jsonPath("$.characters[0].rank").value("MASTER"))
                .andExpect(jsonPath("$.characters[0].mr").value(1600))
                .andExpect(jsonPath("$.playTimeStart").exists())
                .andExpect(jsonPath("$.playTimeEnd").exists())
                .andExpect(jsonPath("$.message").value("よろしくお願いします"))
                .andExpect(jsonPath("$.xId").value("testxid"))
                .andExpect(jsonPath("$.discordId").value("test#1234"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.deleteFlag").doesNotExist());
    }

    @Test
    void findById_UserServiceがUserNotFoundExceptionを投げた場合_404を返す() throws Exception {

        when(userService.findById(999L)).thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found. id=999"))
                .andExpect(jsonPath("$.path").value("/api/users/999"));
    }

    @Test
    void findById_未認証でGET_apiUsersMeを送信した場合_数値ID用permitAllにマッチせず認証を要求される() throws Exception {

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(userService, never()).findById(any());
    }
}
