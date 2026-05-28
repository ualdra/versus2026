package com.versus.api.questions.proposals;

import com.versus.api.questions.proposals.dto.ProposeQuestionRequest;
import com.versus.api.questions.proposals.dto.QuestionProposalHistoryResponse;
import com.versus.api.questions.proposals.dto.QuestionProposalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Question proposals", description = "Community question proposals")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionProposalController {

    private final QuestionProposalService proposalService;

    @Operation(summary = "Propose a new community question")
    @PostMapping("/propose")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionProposalResponse propose(@Valid @RequestBody ProposeQuestionRequest request,
                                            @AuthenticationPrincipal UUID authorId) {
        return proposalService.propose(authorId, request);
    }

    @Operation(summary = "List my question proposals")
    @GetMapping("/proposals/me")
    public QuestionProposalHistoryResponse mine(@AuthenticationPrincipal UUID authorId) {
        return proposalService.listMine(authorId);
    }
}
