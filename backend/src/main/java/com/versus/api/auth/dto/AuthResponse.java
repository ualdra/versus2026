package com.versus.api.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, AuthUser user) {
    public record AuthUser(String id, String username, String role, String avatarUrl) { }
}
