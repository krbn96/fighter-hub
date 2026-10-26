package com.fighterhub.service;

import java.util.Optional;

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

    // user不在・passwordHash未設定の場合に照合するダミーのBCryptハッシュ値。
    // 起動時(このService Bean構築時)に1回だけ生成し、以降はリクエストごとに
    // 生成し直さない(BCryptのencode自体が意図的に低速なため、都度生成すると
    // そのコストがタイミング差として新たに現れてしまう)。
    private static final String DUMMY_PASSWORD_FOR_TIMING_EQUALIZATION =
            "timing-equalization-dummy-password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginRateLimiter loginRateLimiter;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.loginRateLimiter = loginRateLimiter;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD_FOR_TIMING_EQUALIZATION);
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
    //
    // user不在・passwordHash未設定・パスワード不一致のいずれの経路でも、
    // PasswordEncoder.matchesを必ず1回だけ実行する(user不在/passwordHash未設定の場合は
    // dummyPasswordHashと比較する)ことで、これらの経路間の処理時間差を軽減する。
    // 実在ユーザーでパスワードが不一致の場合も、既存どおり実際のpasswordHashとの照合を1回だけ行う。
    @Transactional(readOnly = true)
    public AuthLoginResponse login(AuthLoginRequest request, String clientIp) {
        LoginRateLimiter.Decision decision = loginRateLimiter.checkAllowed(clientIp, request.email());
        if (!decision.allowed()) {
            throw new TooManyLoginAttemptsException(decision.retryAfterSeconds());
        }

        Optional<User> userOptional = userRepository.findByEmailAndDeleteFlagFalse(request.email());
        String actualPasswordHash = userOptional.map(User::getPasswordHash).orElse(null);
        boolean hasPasswordHash = actualPasswordHash != null;

        boolean passwordMatches = passwordEncoder.matches(
                request.password(), hasPasswordHash ? actualPasswordHash : dummyPasswordHash);

        if (!hasPasswordHash || !passwordMatches) {
            loginRateLimiter.recordFailure(clientIp, request.email());
            throw new AuthenticationFailedException();
        }

        User user = userOptional.get();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(user.getId());
        String accessToken = jwtProvider.generateToken(authenticatedUser.userId());

        loginRateLimiter.recordSuccess(clientIp, request.email());

        return new AuthLoginResponse(accessToken);
    }
}
