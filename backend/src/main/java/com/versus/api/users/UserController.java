package com.versus.api.users;

import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.users.dto.ChangePasswordRequest;
import com.versus.api.users.dto.UpdateMeRequest;
import com.versus.api.users.dto.UpdateAvatarRequest;
import com.versus.api.users.dto.UserMeResponse;
import com.versus.api.users.dto.UserPublicResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Users", description = "User profile management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get the authenticated user's full profile",
            responses = @ApiResponse(responseCode = "200", description = "Profile returned"))
    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal UUID userId) {
        return userService.getMe(userId);
    }

    @Operation(summary = "Update the authenticated user's profile",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Profile updated"),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Username already taken",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping("/me")
    public UserMeResponse updateMe(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody UpdateMeRequest req) {
        return userService.updateMe(userId, req);
    }

    @Operation(summary = "Change the authenticated user's password",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Password changed"),
                    @ApiResponse(responseCode = "401", description = "Current password is incorrect",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal UUID userId,
                               @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(userId, req);
    }
  
    @Operation(summary = "Select a predefined avatar URL",
            responses = @ApiResponse(responseCode = "200", description = "Avatar updated"))
    @PutMapping(value = "/me/avatar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserMeResponse updateAvatar(@AuthenticationPrincipal UUID userId,
                                       @Valid @RequestBody UpdateAvatarRequest req) {
        return userService.updateAvatar(userId, req.avatarUrl());
    }

    @Operation(summary = "Soft delete the authenticated user's account",
            responses = @ApiResponse(responseCode = "204", description = "Account deleted"))
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal UUID userId) {
        userService.deleteMe(userId);
    }
  
    @Operation(summary = "Upload and set the authenticated user's avatar",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Avatar updated"),
                    @ApiResponse(responseCode = "400", description = "Invalid image",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserMeResponse updateAvatar(@AuthenticationPrincipal UUID userId,
                                       @RequestParam("file") MultipartFile file) {
        return userService.updateAvatar(userId, file);
    }

    @Operation(summary = "Get a user's public profile by ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Public profile returned"),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/{id}")
    public UserPublicResponse byId(@PathVariable("id") UUID id) {
        return userService.getPublic(id);
    }
}
