package com.versus.api.social.dto;

import java.util.UUID;

public record FriendRequestEvent(UUID requestId, SocialUserResponse from) {
}
