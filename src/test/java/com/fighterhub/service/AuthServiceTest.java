package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.dto.AuthLoginResponse;
import com.fighterhub.entity.User;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.exception.TooManyLoginAttemptsException;
import com.fighterhub.repository.UserRepository;
import com.fighterhub.security.JwtProvider;
import com.fighterhub.security.LoginRateLimiter;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CLIENT_IP = "203.0.113.5";

    // AuthServiceのコンストラクタがpasswordEncoder.encode(...)で生成するダミーハッシュを
    // 固定値にするためのstub値。この値自体に意味はなく、「user不在/passwordHash未設定の場合、
    // 必ずこの値を使ってmatchesが1回呼ばれる」ことをテストで検証できるようにするためのもの。
    private static final String DUMMY_PASSWORD_HASH = "dummy-password-hash-for-test";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    private AuthService authService;

    // @InjectMocksを使わず、AuthServiceのコンストラクタ呼び出し前にpasswordEncoder.encode(...)を
    // stubする(コンストラクタ内でダミーハッシュを1回生成するため、@InjectMocksの自動注入では
    // このstubが間に合わず、ダミーハッシュがMockitoの既定値(null)になってしまう)。
    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(any())).thenReturn(DUMMY_PASSWORD_HASH);
        authService = new AuthService(userRepository, passwordEncoder, jwtProvider, loginRateLimiter);
    }

    @Test
    void login_正しいemailとpasswordの場合_AccessTokenを含むレスポンスを返す() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");

        when(loginRateLimiter.checkAllowed(CLIENT_IP, "user@example.com"))
                .thenReturn(new LoginRateLimiter.Decision(true, 0));

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getPasswordHash()).thenReturn("hashed-password");

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtProvider.generateToken(1L)).thenReturn("generated-token");

        AuthLoginResponse result = authService.login(request, CLIENT_IP);

        assertEquals("generated-token", result.accessToken());

        verify(userRepository).findByEmailAndDeleteFlagFalse("user@example.com");
        verify(passwordEncoder, times(1)).matches("password123", "hashed-password");
        verify(jwtProvider).generateToken(1L);
        verify(loginRateLimiter).recordSuccess(CLIENT_IP, "user@example.com");
        verify(loginRateLimiter, never()).recordFailure(any(), any());
    }

    @Test
    void login_emailが存在しない場合_ダミーハッシュでmatchesが1回呼ばれAuthenticationFailedExceptionを投げ失敗が記録される() {

        AuthLoginRequest request = new AuthLoginRequest("nouser@example.com", "password123");

        when(loginRateLimiter.checkAllowed(CLIENT_IP, "nouser@example.com"))
                .thenReturn(new LoginRateLimiter.Decision(true, 0));
        when(userRepository.findByEmailAndDeleteFlagFalse("nouser@example.com"))
                .thenReturn(Optional.empty());

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.login(request, CLIENT_IP));

        assertEquals("Invalid email or password.", exception.getMessage());
        // user不在でも、実在ユーザーのパスワード不一致と同じくmatchesが"1回だけ"呼ばれる
        // (タイミング差軽減のため。ただし比較対象はダミーハッシュ)。
        verify(passwordEncoder, times(1)).matches("password123", DUMMY_PASSWORD_HASH);
        verify(jwtProvider, never()).generateToken(any());
        verify(loginRateLimiter).recordFailure(CLIENT_IP, "nouser@example.com");
        verify(loginRateLimiter, never()).recordSuccess(any(), any());
    }

    @Test
    void login_passwordが一致しない場合_実際のpasswordHashでmatchesが1回呼ばれAuthenticationFailedExceptionを投げ失敗が記録される() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "wrong-password");

        when(loginRateLimiter.checkAllowed(CLIENT_IP, "user@example.com"))
                .thenReturn(new LoginRateLimiter.Decision(true, 0));

        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn("hashed-password");

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.login(request, CLIENT_IP));

        assertEquals("Invalid email or password.", exception.getMessage());
        verify(passwordEncoder, times(1)).matches("wrong-password", "hashed-password");
        verify(jwtProvider, never()).generateToken(any());
        verify(loginRateLimiter).recordFailure(CLIENT_IP, "user@example.com");
    }

    @Test
    void login_passwordHashがnullの場合_ダミーハッシュでmatchesが1回呼ばれJwtProviderは呼ばれずAuthenticationFailedExceptionを投げ失敗が記録される() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");

        when(loginRateLimiter.checkAllowed(CLIENT_IP, "user@example.com"))
                .thenReturn(new LoginRateLimiter.Decision(true, 0));

        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn(null);

        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(user));

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.login(request, CLIENT_IP));

        assertEquals("Invalid email or password.", exception.getMessage());
        // passwordHash未設定でも、matchesが"1回だけ"呼ばれる(比較対象はダミーハッシュ)。
        verify(passwordEncoder, times(1)).matches("password123", DUMMY_PASSWORD_HASH);
        verify(jwtProvider, never()).generateToken(any());
        verify(loginRateLimiter).recordFailure(CLIENT_IP, "user@example.com");
    }

    @Test
    void login_レート制限で拒否されている場合_TooManyLoginAttemptsExceptionを投げDBアクセスもパスワード照合も行わない() {

        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");

        when(loginRateLimiter.checkAllowed(CLIENT_IP, "user@example.com"))
                .thenReturn(new LoginRateLimiter.Decision(false, 123));

        TooManyLoginAttemptsException exception = assertThrows(
                TooManyLoginAttemptsException.class,
                () -> authService.login(request, CLIENT_IP));

        assertEquals(123, exception.getRetryAfterSeconds());
        verify(userRepository, never()).findByEmailAndDeleteFlagFalse(any());
        // 429(レート制限)の場合は、ダミー照合も含めてPasswordEncoder.matchesを一切呼ばない。
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtProvider, never()).generateToken(any());
        verify(loginRateLimiter, never()).recordFailure(any(), any());
        verify(loginRateLimiter, never()).recordSuccess(any(), any());
    }

    @Test
    void login_emailが存在しない場合とpasswordHashが未設定の場合は同じ回数だけmatchesが呼ばれる() {
        // 「処理時間そのものに依存する不安定なテストは作らない」方針のため、実際の経過時間は
        // 計測しない。代わりに、どちらの経路でもPasswordEncoder.matchesが"ちょうど1回"
        // 呼ばれるという、時間差の原因を構造的に揃えたことそのものを検証する。

        AuthLoginRequest notFoundRequest = new AuthLoginRequest("nouser@example.com", "password123");
        when(loginRateLimiter.checkAllowed(CLIENT_IP, "nouser@example.com"))
                .thenReturn(new LoginRateLimiter.Decision(true, 0));
        when(userRepository.findByEmailAndDeleteFlagFalse("nouser@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class,
                () -> authService.login(notFoundRequest, CLIENT_IP));

        AuthLoginRequest nullHashRequest = new AuthLoginRequest("user@example.com", "password123");
        when(loginRateLimiter.checkAllowed(CLIENT_IP, "user@example.com"))
                .thenReturn(new LoginRateLimiter.Decision(true, 0));
        User userWithoutHash = mock(User.class);
        when(userWithoutHash.getPasswordHash()).thenReturn(null);
        when(userRepository.findByEmailAndDeleteFlagFalse("user@example.com"))
                .thenReturn(Optional.of(userWithoutHash));

        assertThrows(AuthenticationFailedException.class,
                () -> authService.login(nullHashRequest, CLIENT_IP));

        // 両方の経路("password123"というsame raw passwordで)で、ダミーハッシュとの
        // matchesがそれぞれ1回ずつ、合計2回呼ばれている(経路によって呼ばれる/呼ばれない、
        // という差が無い)。
        verify(passwordEncoder, times(2)).matches("password123", DUMMY_PASSWORD_HASH);
    }
}
