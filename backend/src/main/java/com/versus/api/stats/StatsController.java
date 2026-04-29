package com.versus.api.stats;

import com.versus.api.match.GameMode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/me")
    public Object mine(@AuthenticationPrincipal UUID userId,
                       @RequestParam(required = false) GameMode mode) {
        if (mode != null) {
            return statsService.getMine(userId, mode);
        }
        return statsService.getMine(userId);
    }
}
