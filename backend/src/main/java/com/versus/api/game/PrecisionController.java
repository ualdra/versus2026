package com.versus.api.game;

import com.versus.api.game.dto.PrecisionAnswerRequest;
import com.versus.api.game.dto.PrecisionAnswerResponse;
import com.versus.api.game.dto.StartGameResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/game/precision")
@RequiredArgsConstructor
public class PrecisionController {

    private final GameService gameService;

    @PostMapping("/start")
    public StartGameResponse start(@AuthenticationPrincipal UUID userId) {
        return gameService.startPrecision(userId);
    }

    @PostMapping("/answer")
    public PrecisionAnswerResponse answer(@AuthenticationPrincipal UUID userId,
                                          @Valid @RequestBody PrecisionAnswerRequest request) {
        return gameService.answerPrecision(userId, request);
    }
}
