package com.fighterhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fighterhub.config.SecurityConfig;
import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.dto.AuthLoginResponse;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.service.AuthService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void login_未認証で正しいemailとpasswordを送信した場合_200とaccessTokenを返す() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "password123"
                }
                """;

        when(authService.login(any(AuthLoginRequest.class)))
                .thenReturn(new AuthLoginResponse("generated-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("generated-token"));

        verify(authService, times(1)).login(any(AuthLoginRequest.class));
    }

    @Test
    void login_AuthServiceが認証失敗をthrowした場合_401を返す() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "wrong-password"
                }
                """;

        when(authService.login(any(AuthLoginRequest.class)))
                .thenThrow(new AuthenticationFailedException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void login_email形式が不正な場合_400を返しloginは呼ばれない() throws Exception {

        String requestBody = """
                {
                  "email": "invalid-email",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_emailがblankの場合_400を返しloginは呼ばれない() throws Exception {

        String requestBody = """
                {
                  "email": "",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_passwordがblankの場合_400を返しloginは呼ばれない() throws Exception {

        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": ""
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }
}
