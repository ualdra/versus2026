package com.versus.api.social.repo;

import com.versus.api.social.SocialStatus;
import com.versus.api.social.domain.MatchInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchInviteRepository extends JpaRepository<MatchInvite, UUID> {
    List<MatchInvite> findByToUserIdAndStatusOrderByCreatedAtDesc(UUID toUserId, SocialStatus status);
    List<MatchInvite> findByFromUserIdOrderByCreatedAtDesc(UUID fromUserId);
    Optional<MatchInvite> findByIdAndToUserId(UUID id, UUID toUserId);
    boolean existsByMatchIdAndToUserIdAndStatus(UUID matchId, UUID toUserId, SocialStatus status);
}
