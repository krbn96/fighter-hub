package com.fighterhub.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.dto.AuthLoginResponse;
import com.fighterhub.entity.User;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.repository.UserRepository;
import com.fighterhub.security.JwtProvider;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    // JWT生成に必要な最小限の情報のみを持つ、Controller層には公開しない内部専用の認証結果。
    // JWTにemailを含めない設計のため、userIdのみを保持する。
    public record AuthenticatedUser(Long userId) {
    }

    @Transactional(readOnly = true)
    public AuthLoginResponse login(AuthLoginRequest request) {
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

        return new AuthLoginResponse(accessToken);
    }
}
