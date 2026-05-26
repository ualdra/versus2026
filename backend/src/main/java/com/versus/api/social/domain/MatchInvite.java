package com.versus.api.social.domain;

import com.versus.api.match.GameMode;
import com.versus.api.social.SocialStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_invites", indexes = {
        @Index(name = "idx_match_invites_to_status", columnList = "to_user_id,status,created_at"),
        @Index(name = "idx_match_invites_from_created", columnList = "from_user_id,created_at"),
        @Index(name = "idx_match_invites_match", columnList = "match_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchInvite {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SocialStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = SocialStatus.PENDING;
    }
}
