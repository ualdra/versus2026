package com.versus.api.questions.proposals.dto;

import com.versus.api.questions.QuestionType;
import com.versus.api.questions.proposals.ProposalStatus;

import java.time.Instant;
import java.util.UUID;

public record QuestionProposalResponse(
        UUID id,
        UUID authorId,
        QuestionType type,
        String text,
        String proposedAnswer,
        String alternativeAnswer,
        String category,
        ProposalStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectReason,
        String sourceUrl,
        Instant createdAt
) {}
