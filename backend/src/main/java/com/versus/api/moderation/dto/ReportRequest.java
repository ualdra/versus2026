package com.versus.api.moderation.dto;

import com.versus.api.moderation.ReportReason;
import jakarta.validation.constraints.NotNull;

public record ReportRequest(
        @NotNull ReportReason reason,
        String comment
) {}
