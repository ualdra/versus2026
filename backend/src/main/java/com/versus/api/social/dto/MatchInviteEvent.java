package com.versus.api.social.dto;

import com.versus.api.match.GameMode;

import java.util.UUID;

public record MatchInviteEvent(
        UUID inviteId,
        UUID matchId,
        GameMode mode,
        SocialUserResponse from) {
}
