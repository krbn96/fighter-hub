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
import com.fighterhub.entity.User;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void authenticate_正しいemailとpasswordの場合_認証に成功する() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPasswordHash()).thenReturn("hashed-password");

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);

        AuthService.AuthenticatedUser result = authService.authenticate(request);

        assertEquals(1L, result.userId());
        assertEquals("user@example.com", result.email());

        verify(userRepository).findByEmailAndDeleteFlagFalse("user@example.com");
        verify(passwordEncoder).matches("password123", "hashed-password");
    }

    @Test
    void authenticate_emailが存在しない場合_AuthenticationFailedExceptionを投げる() {

        AuthLoginRequest request = new AuthLoginRequest("nouser@example.com", "password123");

        when(userRepository.findByEmailAndDeleteFlagFalse("nouser@example.com"))
                .thenReturn(Optional.empty());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.authenticate(request));

        assertEquals("Invalid email or password.", exception.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void authenticate_passwordが一致しない場合_AuthenticationFailedExceptionを投げる() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "wrong-password");

        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn("hashed-password");

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.authenticate(request));

        assertEquals("Invalid email or password.", exception.getMessage());
        verify(passwordEncoder).matches("wrong-password", "hashed-password");
    }

    @Test
    void authenticate_passwordHashがnullの場合_matchesを呼ばずAuthenticationFailedExceptionを投げる() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");

        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn(null);

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.authenticate(request));

        assertEquals("Invalid email or password.", exception.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
