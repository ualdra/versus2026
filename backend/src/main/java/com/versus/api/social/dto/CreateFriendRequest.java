package com.versus.api.social.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateFriendRequest(@NotNull UUID toUserId) {
}
