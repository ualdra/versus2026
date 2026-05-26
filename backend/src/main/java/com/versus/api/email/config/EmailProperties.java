package com.versus.api.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "versus.email")
public record EmailProperties(
        String from,
        String fromName,
        boolean enabled
) {
    public EmailProperties {
        if (from == null || from.isBlank()) {
            from = "noreply@versus-game.com";
        }
        if (fromName == null || fromName.isBlank()) {
            fromName = "Versus Game";
        }
    }
}
