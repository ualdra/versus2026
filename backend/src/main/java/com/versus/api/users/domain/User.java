package com.versus.api.users.domain;

import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_username", columnList = "username", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** false hasta que el usuario verifique su correo */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Column(name = "verification_token_expiry")
    private Instant verificationTokenExpiry;

    @Column(name = "password_reset_token", length = 64)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    private Instant passwordResetTokenExpiry;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (isActive == null) isActive = true;
        if (enabled == null) enabled = false;
        if (role == null) role = Role.PLAYER;
        if (status == null) status = UserStatus.ACTIVE;
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
