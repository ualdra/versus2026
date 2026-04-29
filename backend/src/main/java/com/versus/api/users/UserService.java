package com.versus.api.users;

import com.versus.api.common.exception.ApiException;
import com.versus.api.users.domain.User;
import com.versus.api.users.dto.UpdateMeRequest;
import com.versus.api.users.dto.UserMeResponse;
import com.versus.api.users.dto.UserPublicResponse;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(UUID userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return toMe(u);
    }

    @Transactional
    public UserMeResponse updateMe(UUID userId, UpdateMeRequest req) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (req.username() != null && !req.username().equals(u.getUsername())) {
            if (users.existsByUsername(req.username())) {
                throw ApiException.conflict("Username already taken");
            }
            u.setUsername(req.username());
        }
        if (req.avatarUrl() != null) {
            u.setAvatarUrl(req.avatarUrl());
        }
        users.save(u);
        return toMe(u);
    }

    @Transactional(readOnly = true)
    public UserPublicResponse getPublic(UUID userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return new UserPublicResponse(
                u.getId().toString(),
                u.getUsername(),
                u.getAvatarUrl(),
                u.getRole().name(),
                u.getCreatedAt());
    }

    private UserMeResponse toMe(User u) {
        return new UserMeResponse(
                u.getId().toString(),
                u.getUsername(),
                u.getEmail(),
                u.getAvatarUrl(),
                u.getRole().name(),
                u.getCreatedAt());
    }
}
