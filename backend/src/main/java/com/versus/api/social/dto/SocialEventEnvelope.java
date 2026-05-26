package com.versus.api.social.dto;

public record SocialEventEnvelope<T>(String type, T payload) {
    public static <T> SocialEventEnvelope<T> of(String type, T payload) {
        return new SocialEventEnvelope<>(type, payload);
    }
}
