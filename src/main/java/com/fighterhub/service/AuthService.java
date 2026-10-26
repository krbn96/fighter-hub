package com.fighterhub.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.dto.AuthLoginResponse;
import com.fighterhub.entity.User;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.exception.TooManyLoginAttemptsException;
import com.fighterhub.repository.UserRepository;
import com.fighterhub.security.JwtProvider;
import com.fighterhub.security.LoginRateLimiter;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.loginRateLimiter = loginRateLimiter;
    }

    // JWT生成に必要な最小限の情報のみを持つ、Controller層には公開しない内部専用の認証結果。
    // JWTにemailを含めない設計のため、userIdのみを保持する。
    public record AuthenticatedUser(Long userId) {
    }

    // clientIpはController層がHttpServletRequestから取得して渡す(本番ではforward-headers-strategy
    // によりnginx/Caddy経由でも実クライアントIPに解決される。詳細はclaude-report.md参照)。
    //
    // レート制限の判定はemailがDBに実在するかを問わずemail/IPの文字列のみで行う
    // (実在/非実在のemailで応答が変わるオラクルを作らないため)。閾値到達時は
    // パスワード照合(BCrypt)自体を実行せずに429を返す。
    @Transactional(readOnly = true)
    public AuthLoginResponse login(AuthLoginRequest request, String clientIp) {
        LoginRateLimiter.Decision decision = loginRateLimiter.checkAllowed(clientIp, request.email());
        if (!decision.allowed()) {
            throw new TooManyLoginAttemptsException(decision.retryAfterSeconds());
        }

        try {
            User user = userRepository.findByEmailAndDeleteFlagFalse(request.email())
                    .orElseThrow(AuthenticationFailedException::new);

            if (user.getPasswordHash() == null) {
                throw new AuthenticationFailedException();
            }

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new AuthenticationFailedException();
            }

            AuthenticatedUser authenticatedUser = new AuthenticatedUser(user.getId());
            String accessToken = jwtProvider.generateToken(authenticatedUser.userId());

            loginRateLimiter.recordSuccess(clientIp, request.email());

            return new AuthLoginResponse(accessToken);
        } catch (AuthenticationFailedException ex) {
            loginRateLimiter.recordFailure(clientIp, request.email());
            throw ex;
        }
    }
}
