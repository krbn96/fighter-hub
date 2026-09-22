package com.fighterhub.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.entity.User;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // JWT生成(Day 5)に必要な最小限の情報のみを持つ、Controller層には公開しない内部専用の認証結果。
    // User EntityをそのままServiceの外へ返さないため、passwordHash等を含む全フィールドを露出させない。
    public record AuthenticatedUser(Long userId, String email) {
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(AuthLoginRequest request) {
        User user = userRepository.findByEmailAndDeleteFlagFalse(request.email())
                .orElseThrow(AuthenticationFailedException::new);

        if (user.getPasswordHash() == null) {
            throw new AuthenticationFailedException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }

        return new AuthenticatedUser(user.getId(), user.getEmail());
    }
}
