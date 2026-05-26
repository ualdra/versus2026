package com.versus.api.users.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateMeRequest(
        @Size(min = 3, max = 64) String username,
        @Size(max = 512) String avatarUrl
) { }
