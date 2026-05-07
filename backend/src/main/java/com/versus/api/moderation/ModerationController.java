package com.versus.api.moderation;

import com.versus.api.moderation.dto.ReportResponse;
import com.versus.api.moderation.dto.ResolveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "Moderation", description = "Report management (MODERATOR+)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/moderation/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
public class ModerationController {

    private final ModerationService moderationService;

    @Operation(summary = "List reports",
            responses = @ApiResponse(responseCode = "200", description = "Report list returned"))
    @GetMapping
    public Page<ReportResponse> list(
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return moderationService.listReports(status, pageable);
    }

    @Operation(summary = "Resolve a report",
            responses = {
                @ApiResponse(responseCode = "200", description = "Report resolved"),
                @ApiResponse(responseCode = "404", description = "Report not found"),
                @ApiResponse(responseCode = "409", description = "Report already resolved")
            })
    @PutMapping("/{id}/resolve")
    public ReportResponse resolve(@PathVariable UUID id,
                                  @Valid @RequestBody ResolveRequest req,
                                  @AuthenticationPrincipal UUID moderatorId) {
        return moderationService.resolve(id, moderatorId, req);
    }
}
