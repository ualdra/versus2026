package com.versus.api.questions.proposals.dto;

import java.util.List;

public record QuestionProposalHistoryResponse(
        int pendingCount,
        int pendingLimit,
        List<QuestionProposalResponse> proposals
) {}
