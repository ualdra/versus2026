package com.versus.api.practice;

import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.practice.dto.PracticeAnswerRequest;
import com.versus.api.practice.dto.PracticeAnswerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Practice", description = "Practice mode — answer questions and see the correct answer immediately, no session or stats recorded")
@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;

    @Operation(summary = "Evaluate a practice answer",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Answer evaluated with correct answer and explanation"),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Question not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/answer")
    public PracticeAnswerResponse answer(@Valid @RequestBody PracticeAnswerRequest request) {
        return practiceService.evaluate(request);
    }
}
