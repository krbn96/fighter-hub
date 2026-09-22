package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.dto.AuthLoginResponse;
import com.fighterhub.entity.User;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.repository.UserRepository;
import com.fighterhub.security.JwtProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_正しいemailとpasswordの場合_AccessTokenを含むレスポンスを返す() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getPasswordHash()).thenReturn("hashed-password");

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtProvider.generateToken(1L)).thenReturn("generated-token");

        AuthLoginResponse result = authService.login(request);

        assertEquals("generated-token", result.accessToken());

        verify(userRepository).findByEmailAndDeleteFlagFalse("user@example.com");
        verify(passwordEncoder).matches("password123", "hashed-password");
        verify(jwtProvider).generateToken(1L);
    }

    @Test
    void login_emailが存在しない場合_AuthenticationFailedExceptionを投げJwtProviderは呼ばれない() {

        AuthLoginRequest request = new AuthLoginRequest("nouser@example.com", "password123");

        when(userRepository.findByEmailAndDeleteFlagFalse("nouser@example.com"))
                .thenReturn(Optional.empty());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.login(request));

        assertEquals("Invalid email or password.", exception.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtProvider, never()).generateToken(any());
    }

    @Test
    void login_passwordが一致しない場合_AuthenticationFailedExceptionを投げJwtProviderは呼ばれない() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "wrong-password");

        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn("hashed-password");

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.login(request));

        assertEquals("Invalid email or password.", exception.getMessage());
        verify(passwordEncoder).matches("wrong-password", "hashed-password");
        verify(jwtProvider, never()).generateToken(any());
    }

    @Test
    void login_passwordHashがnullの場合_matchesもJwtProviderも呼ばずAuthenticationFailedExceptionを投げる() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");

        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn(null);

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.login(request));

        assertEquals("Invalid email or password.", exception.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtProvider, never()).generateToken(any());
    }
}
