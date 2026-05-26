package com.versus.api.social.repo;

import com.versus.api.social.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    boolean existsByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);
    List<Friendship> findByUserLowIdOrUserHighId(UUID userLowId, UUID userHighId);
    long countByUserLowIdOrUserHighId(UUID userLowId, UUID userHighId);
}
