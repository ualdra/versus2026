package com.versus.api.moderation.dto;

import com.versus.api.moderation.ResolveAction;
import jakarta.validation.constraints.NotNull;

public record ResolveRequest(
        @NotNull ResolveAction action
) {}
