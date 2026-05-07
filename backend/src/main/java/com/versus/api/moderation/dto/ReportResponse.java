package com.versus.api.moderation.dto;

import com.versus.api.moderation.ReportReason;
import com.versus.api.moderation.ReportStatus;
import com.versus.api.moderation.ResolveAction;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID questionId,
        String questionText,
        String questionType,
        String questionCategory,
        ReportReason reason,
        String comment,
        ReportStatus status,
        Instant createdAt,
        UUID resolvedBy,
        Instant resolvedAt,
        ResolveAction action
) {}
