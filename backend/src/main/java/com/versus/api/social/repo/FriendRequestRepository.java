package com.versus.api.social.repo;

import com.versus.api.social.SocialStatus;
import com.versus.api.social.domain.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {
    List<FriendRequest> findByAddresseeIdAndStatusOrderByCreatedAtDesc(UUID addresseeId, SocialStatus status);
    List<FriendRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(UUID requesterId, SocialStatus status);
    Optional<FriendRequest> findByIdAndAddresseeId(UUID id, UUID addresseeId);
    Optional<FriendRequest> findByIdAndRequesterId(UUID id, UUID requesterId);

    @Query("""
            SELECT fr FROM FriendRequest fr
            WHERE fr.status = :status
              AND ((fr.requesterId = :userA AND fr.addresseeId = :userB)
                   OR (fr.requesterId = :userB AND fr.addresseeId = :userA))
            """)
    Optional<FriendRequest> findPendingBetween(@Param("userA") UUID userA,
                                               @Param("userB") UUID userB,
                                               @Param("status") SocialStatus status);
}
