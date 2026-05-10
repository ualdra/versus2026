package com.versus.api.users;

import com.versus.api.common.exception.ApiException;
import com.versus.api.media.MediaService;
import com.versus.api.media.dto.MediaAssetResponse;
import com.versus.api.users.domain.User;
import com.versus.api.users.dto.ChangePasswordRequest;
import com.versus.api.users.dto.UpdateMeRequest;
import com.versus.api.users.dto.UserMeResponse;
import com.versus.api.users.dto.UserPublicResponse;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final MediaService mediaService;


    @Transactional(readOnly = true)
    public UserMeResponse getMe(UUID userId) {
        User u = activeUser(userId);
        return toMe(u);
    }

    @Transactional
    public UserMeResponse updateMe(UUID userId, UpdateMeRequest req) {
        User u = activeUser(userId);
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

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User u = activeUser(userId);
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.unauthorized("Current password is incorrect");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(u);
    }

    @Transactional
    public UserMeResponse updateAvatar(UUID userId, String avatarUrl) {
        User u = activeUser(userId);
        if (avatarUrl == null || avatarUrl.isBlank()) {
            throw ApiException.validation("Avatar URL is required");
        }
        if (avatarUrl.length() > 512) {
            throw ApiException.validation("Avatar URL must be at most 512 characters");
        }
        u.setAvatarUrl(avatarUrl);
        users.save(u);
        return toMe(u);
    }  

    @Transactional
    public UserMeResponse updateAvatar(UUID userId, MultipartFile file) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        MediaAssetResponse avatar = mediaService.uploadAvatar(userId, file);
        u.setAvatarUrl(avatar.url());
        users.save(u);
        return toMe(u);
    }


    /** DEAD CODE: Comprobar validaciones para aplicar guards de imágenes
    @Transactional
    public UserMeResponse updateAvatarUpload(UUID userId, byte[] bytes, String contentType) {
        User u = activeUser(userId);
        if (bytes == null || bytes.length == 0) {
            throw ApiException.validation("Avatar file is required");
        }
        if (bytes.length > 2 * 1024 * 1024) {
            throw ApiException.validation("Avatar must be at most 2MB");
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.validation("Avatar must be an image");
        }
        // Temporary local persistence until the storage module is available.
        u.setAvatarUrl("data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes));
        users.save(u);
        return toMe(u);
    }
    */

    @Transactional
    public void deleteMe(UUID userId) {
        User u = activeUser(userId);
        String deletedId = u.getId().toString();
        u.setUsername("deleted-" + deletedId);
        u.setEmail("deleted-" + deletedId + "@deleted.local");
        u.setAvatarUrl(null);
        u.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        u.setStatus(UserStatus.DELETED);
        u.setIsActive(false);
        users.save(u);
    }

    @Transactional(readOnly = true)
    public UserPublicResponse getPublic(UUID userId) {
        User u = activeUser(userId);
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

    private User activeUser(UUID userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (UserStatus.DELETED.equals(u.getStatus()) || Boolean.FALSE.equals(u.getIsActive())) {
            throw ApiException.notFound("User not found");
        }
        return u;
    }
}
