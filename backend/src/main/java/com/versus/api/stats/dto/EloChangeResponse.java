package com.versus.api.stats.dto;

public record EloChangeResponse(
        int previousRating,
        int currentRating,
        int delta) {
}
