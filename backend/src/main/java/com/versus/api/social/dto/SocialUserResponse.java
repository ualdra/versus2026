package com.versus.api.social.dto;

import com.versus.api.social.SocialRelation;

import java.util.UUID;

public record SocialUserResponse(
        UUID userId,
        String username,
        String avatarUrl,
        SocialRelation relation) {
}
