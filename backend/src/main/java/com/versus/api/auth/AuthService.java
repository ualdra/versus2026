package com.versus.api.auth;

import com.versus.api.auth.domain.RefreshToken;
import com.versus.api.auth.dto.AuthResponse;
import com.versus.api.auth.dto.LoginRequest;
import com.versus.api.auth.dto.MessageResponse;
import com.versus.api.auth.dto.PasswordResetConfirmRequest;
import com.versus.api.auth.dto.PasswordResetRequest;
import com.versus.api.auth.dto.RegisterRequest;
import com.versus.api.auth.repo.RefreshTokenRepository;
import com.versus.api.common.exception.ApiException;
import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final EmailService emailService;

    @Value("${versus.auth.verification-token-expiry:86400}")
    private long verificationTokenExpiry;

    @Value("${versus.auth.reset-token-expiry:900}")
    private long resetTokenExpiry;

    @Transactional
    public MessageResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("Email already registered");
        }
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("Username already taken");
        }
        String token = generateSecureToken();
        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.PLAYER)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .enabled(false)
                .verificationToken(token)
                .verificationTokenExpiry(Instant.now().plusSeconds(verificationTokenExpiry))
                .build();
        users.save(user);
        log.info("Dispatching verification email to {}", user.getEmail());
        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), token);
        return new MessageResponse("Registro completado. Por favor, verifica tu correo electrónico para activar tu cuenta.");
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
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw ApiException.emailNotVerified("Email not verified. Please check your inbox.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        User user = users.findByVerificationToken(token)
                .orElseThrow(() -> ApiException.tokenInvalid("Verification token not found"));
        if (Boolean.TRUE.equals(user.getEnabled())) {
            return new MessageResponse("La cuenta ya estaba verificada.");
        }
        if (user.getVerificationTokenExpiry() == null ||
                user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw ApiException.tokenExpired("Verification token has expired. Please register again or request a new verification email.");
        }
        user.setEnabled(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        users.save(user);
        return new MessageResponse("Cuenta verificada correctamente. Ya puedes iniciar sesión.");
    }

    @Transactional
    public MessageResponse requestPasswordReset(PasswordResetRequest req) {
        users.findByEmail(req.email()).ifPresent(user -> {
            String token = generateSecureToken();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiry(Instant.now().plusSeconds(resetTokenExpiry));
            users.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), token);
        });
        // Siempre devuelve el mismo mensaje para no revelar si el email existe
        return new MessageResponse("Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.");
    }

    @Transactional
    public MessageResponse confirmPasswordReset(PasswordResetConfirmRequest req) {
        User user = users.findByPasswordResetToken(req.token())
                .orElseThrow(() -> ApiException.tokenInvalid("Password reset token not found"));
        if (user.getPasswordResetTokenExpiry() == null ||
                user.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw ApiException.tokenExpired("Password reset token has expired. Please request a new one.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        users.save(user);
        return new MessageResponse("Contraseña actualizada correctamente. Ya puedes iniciar sesión.");
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

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
