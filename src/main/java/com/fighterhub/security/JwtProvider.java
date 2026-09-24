package com.fighterhub.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.fighterhub.config.JwtProperties;

// JWT生成を担当する。
// トークン検証はSpring SecurityのJwtDecoder側で行う。
@Component
public class JwtProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtProvider(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public String generateToken(Long userId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.expiration());

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims));

        return jwt.getTokenValue();
    }
}
