package com.versus.api.game;

import com.versus.api.game.dto.StartGameResponse;
import com.versus.api.game.dto.SurvivalAnswerRequest;
import com.versus.api.game.dto.SurvivalAnswerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/game/survival")
@RequiredArgsConstructor
public class SurvivalController {

    private final GameService gameService;

    @PostMapping("/start")
    public StartGameResponse start(@AuthenticationPrincipal UUID userId) {
        return gameService.startSurvival(userId);
    }

    @PostMapping("/answer")
    public SurvivalAnswerResponse answer(@AuthenticationPrincipal UUID userId,
                                         @Valid @RequestBody SurvivalAnswerRequest request) {
        return gameService.answerSurvival(userId, request);
    }
}
