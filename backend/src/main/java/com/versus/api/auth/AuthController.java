package com.versus.api.auth;

import com.versus.api.auth.dto.AuthResponse;
import com.versus.api.auth.dto.LoginRequest;
import com.versus.api.auth.dto.MessageResponse;
import com.versus.api.auth.dto.PasswordResetConfirmRequest;
import com.versus.api.auth.dto.PasswordResetRequest;
import com.versus.api.auth.dto.RefreshRequest;
import com.versus.api.auth.dto.RegisterRequest;
import com.versus.api.common.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Registration, login, token refresh, logout, email verification and password reset")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new player account", responses = {
            @ApiResponse(responseCode = "201", description = "Account created — verification email sent"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email or username already taken", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @Operation(summary = "Log in and obtain JWT tokens", responses = {
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Email not verified", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "Verify email address with token from email link", responses = {
            @ApiResponse(responseCode = "200", description = "Account verified"),
            @ApiResponse(responseCode = "400", description = "Invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Token expired", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/verify")
    public MessageResponse verifyEmail(@RequestParam String token) {
        return authService.verifyEmail(token);
    }

    @Operation(summary = "Request a password reset email", responses = {
            @ApiResponse(responseCode = "200", description = "Email sent if address is registered")
    })
    @PostMapping("/password-reset/request")
    public MessageResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        return authService.requestPasswordReset(req);
    }

    @Operation(summary = "Confirm password reset with token and new password", responses = {
            @ApiResponse(responseCode = "200", description = "Password updated"),
            @ApiResponse(responseCode = "400", description = "Invalid token or validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Token expired", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password-reset/confirm")
    public MessageResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        return authService.confirmPasswordReset(req);
    }

    @Operation(summary = "Exchange a refresh token for a new token pair", responses = {
            @ApiResponse(responseCode = "200", description = "New tokens issued"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @Operation(summary = "Revoke the current refresh token", responses = {
            @ApiResponse(responseCode = "204", description = "Logged out")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest req) {
        authService.logout(req == null ? null : req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
