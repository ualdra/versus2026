package com.versus.api.questions.proposals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectProposalRequest(
        @NotBlank @Size(max = 512) String rejectReason
) {}
