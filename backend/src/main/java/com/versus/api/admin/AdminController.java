package com.versus.api.admin;

import com.versus.api.admin.dto.*;
import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.users.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin", description = "Admin-only management endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "List users (paginated, filterable)",
            responses = @ApiResponse(responseCode = "200", description = "User page returned"))
    @GetMapping("/users")
    public AdminUserPageResponse listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active) {
        return adminService.getUsers(page, size, search, role, active);
    }

    @Operation(summary = "Change a user's role",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Role updated"),
                    @ApiResponse(responseCode = "400", description = "Self-role-change or validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping("/users/{id}/role")
    public AdminUserResponse updateRole(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable("id") UUID targetId,
            @Valid @RequestBody UpdateUserRoleRequest req) {
        return adminService.updateRole(adminId, targetId, req.role());
    }

    @Operation(summary = "Activate or suspend a user account",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Status updated"),
                    @ApiResponse(responseCode = "400", description = "Self-suspend or validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping("/users/{id}/status")
    public AdminUserResponse updateStatus(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable("id") UUID targetId,
            @Valid @RequestBody UpdateUserStatusRequest req) {
        return adminService.updateStatus(adminId, targetId, req.active());
    }

    @Operation(summary = "Get platform KPI stats",
            responses = @ApiResponse(responseCode = "200", description = "Stats returned"))
    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.getStats();
    }

    @Operation(summary = "Get recent activity log entries",
            responses = @ApiResponse(responseCode = "200", description = "Log entries returned"))
    @GetMapping("/logs")
    public List<AdminLogResponse> logs(
            @RequestParam(defaultValue = "20") int limit) {
        int effectiveLimit = Math.min(limit, 100);
        return adminService.getLogs(effectiveLimit);
    }
}
