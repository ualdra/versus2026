package com.versus.api.questions.proposals;

import com.versus.api.questions.proposals.dto.QuestionProposalResponse;
import com.versus.api.questions.proposals.dto.RejectProposalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Moderation", description = "Question proposal moderation (MODERATOR+)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/moderation/proposals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
public class QuestionProposalModerationController {

    private final QuestionProposalService proposalService;

    @Operation(summary = "List community question proposals")
    @GetMapping
    public Page<QuestionProposalResponse> list(
            @RequestParam(required = false) ProposalStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return proposalService.listForModeration(status, pageable);
    }

    @Operation(summary = "Approve a community question proposal")
    @PutMapping("/{id}/approve")
    public QuestionProposalResponse approve(@PathVariable UUID id,
                                            @AuthenticationPrincipal UUID moderatorId) {
        return proposalService.approve(id, moderatorId);
    }

    @Operation(summary = "Reject a community question proposal")
    @PutMapping("/{id}/reject")
    public QuestionProposalResponse reject(@PathVariable UUID id,
                                           @Valid @RequestBody RejectProposalRequest request,
                                           @AuthenticationPrincipal UUID moderatorId) {
        return proposalService.reject(id, moderatorId, request);
    }
}
