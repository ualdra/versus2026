package com.versus.api.achievements;

import com.versus.api.achievements.dto.AchievementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Achievements", description = "Achievement catalog and unlock state")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievements;

    @Operation(summary = "Get all achievements with unlock state for the authenticated user")
    @GetMapping
    public List<AchievementResponse> list(@AuthenticationPrincipal UUID userId) {
        return achievements.listForUser(userId);
    }
}
