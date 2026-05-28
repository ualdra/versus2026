package com.versus.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String identifier,
        @NotBlank String password
) { }
