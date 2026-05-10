package com.versus.api.auth;

import com.versus.api.auth.domain.RefreshToken;
import com.versus.api.auth.dto.AuthResponse;
import com.versus.api.auth.dto.LoginRequest;
import com.versus.api.auth.dto.RegisterRequest;
import com.versus.api.auth.repo.RefreshTokenRepository;
import com.versus.api.common.exception.ApiException;
import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("Email already registered");
        }
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("Username already taken");
        }
        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.PLAYER)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .build();
        users.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
                .orElseThrow(() -> ApiException.unauthorized("Invalid credentials"));
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw ApiException.unauthorized("Account disabled");
        }
        if (UserStatus.DELETED.equals(user.getStatus())) {
            throw ApiException.unauthorized("Account disabled");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        if (!jwt.validate(rawToken)) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        var claims = jwt.parse(rawToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw ApiException.unauthorized("Invalid token type");
        }
        String hash = jwt.hash(rawToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("Refresh token not recognized"));
        if (Boolean.TRUE.equals(stored.getRevoked()) || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token expired or revoked");
        }
        stored.setRevoked(true);
        refreshTokens.save(stored);

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(jwt.hash(rawToken))
                .ifPresent(t -> { t.setRevoked(true); refreshTokens.save(t); });
    }

    private AuthResponse issueTokens(User user) {
        String access = jwt.generateAccessToken(user);
        String refresh = jwt.generateRefreshToken(user);
        refreshTokens.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(jwt.hash(refresh))
                .expiresAt(Instant.now().plusSeconds(jwt.getRefreshExpiry()))
                .revoked(false)
                .build());
        return new AuthResponse(access, refresh,
                new AuthResponse.AuthUser(
                        user.getId().toString(),
                        user.getUsername(),
                        user.getRole().name(),
                        user.getAvatarUrl()));
    }
}
