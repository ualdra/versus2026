package com.versus.api.questions;

import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.moderation.ModerationService;
import com.versus.api.moderation.dto.ReportRequest;
import com.versus.api.moderation.dto.ReportResponse;
import com.versus.api.questions.dto.QuestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Questions", description = "Question retrieval")
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final ModerationService moderationService;

    @Operation(summary = "Get a random active question",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = {
                    @Parameter(name = "type", description = "Filter by type: BINARY or NUMERIC"),
                    @Parameter(name = "category", description = "Filter by category slug")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Question returned"),
                    @ApiResponse(responseCode = "404", description = "No question found for the given filters",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/random")
    public QuestionResponse random(@RequestParam(required = false) QuestionType type,
                                   @RequestParam(required = false) String category) {
        return questionService.getRandom(type, category);
    }

    @Operation(summary = "Get a question by ID",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Question returned"),
                    @ApiResponse(responseCode = "404", description = "Question not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/{id}")
    public QuestionResponse byId(@PathVariable UUID id) {
        return questionService.getById(id);
    }

    @Operation(summary = "List all available category slugs (public)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Category list returned")
            })
    @GetMapping("/categories")
    public List<String> categories() {
        return questionService.getCategories();
    }

    @Operation(summary = "Report a question",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Report created"),
                    @ApiResponse(responseCode = "404", description = "Question not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Already reported",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/{id}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse report(@PathVariable UUID id,
                                 @Valid @RequestBody ReportRequest req,
                                 @AuthenticationPrincipal UUID userId) {
        return moderationService.report(id, userId, req);
    }
}
