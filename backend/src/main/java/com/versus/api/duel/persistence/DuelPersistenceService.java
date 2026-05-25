package com.versus.api.duel.persistence;

import com.versus.api.achievements.AchievementService;
import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.duel.dto.PlayerRoundOutcome;
import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelPlayerRuntime;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.domain.Match;
import com.versus.api.match.domain.MatchAnswer;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import com.versus.api.match.domain.MatchRound;
import com.versus.api.match.repo.MatchAnswerRepository;
import com.versus.api.match.repo.MatchPlayerRepository;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.match.repo.MatchRoundRepository;
import com.versus.api.stats.StatsService;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuelPersistenceService {

    private final MatchRepository matches;
    private final MatchPlayerRepository matchPlayers;
    private final MatchRoundRepository matchRounds;
    private final MatchAnswerRepository matchAnswers;
    private final UserRepository users;
    private final StatsService statsService;
    private final AchievementService achievementService;

    /**
     * Crea filas en match_players para los dos jugadores al arrancar la partida.
     */
    @Transactional
    public void initializeMatchPlayers(UUID matchId, Map<UUID, DuelPlayerRuntime> players, int initialLives) {
        players.values().forEach(runtime -> {
            MatchPlayerId id = new MatchPlayerId(matchId, runtime.getUserId());
            if (matchPlayers.findById(id).isPresent()) return;
            MatchPlayer mp = MatchPlayer.builder()
                    .id(id)
                    .livesRemaining(initialLives)
                    .score(0)
                    .currentStreak(0)
                    .bestStreakInMatch(0)
                    .roundsPlayed(0)
                    .build();
            matchPlayers.save(mp);
        });
    }

    /**
     * Persiste el round y sus respuestas (una por jugador que respondió).
     */
    @Transactional
    public void recordRound(UUID matchId, UUID questionId, int roundNumber, List<PlayerRoundOutcome> outcomes) {
        MatchRound round = matchRounds.save(MatchRound.builder()
                .matchId(matchId)
                .questionId(questionId)
                .roundNumber(roundNumber)
                .build());

        outcomes.stream()
                .filter(PlayerRoundOutcome::answered)
                .forEach(o -> matchAnswers.save(MatchAnswer.builder()
                        .roundId(round.getId())
                        .userId(o.userId())
                        .answerGiven(answerGivenOf(o))
                        .deviation(o.deviation())
                        .lifeDelta(o.lifeDelta())
                        .isCorrect(o.isCorrect())
                        .build()));
    }

    /**
     * Cierra la partida: actualiza match.status, finishedAt y por jugador su resultado,
     * y dispara stats + logros (mismo flujo que GameService.finishMatch + recordFinishedGame).
     */
    @Transactional
    public Map<UUID, List<AchievementResponse>> finalizeMatch(
            UUID matchId,
            GameMode mode,
            Map<UUID, MatchResult> results,
            Map<UUID, DuelPlayerRuntime> runtimes,
            Map<UUID, Double> avgDeviationByUser) {

        matches.findById(matchId).ifPresent(m -> {
            m.setStatus(MatchStatus.FINISHED);
            m.setFinishedAt(Instant.now());
            matches.save(m);
        });

        java.util.Map<UUID, List<AchievementResponse>> unlocked = new java.util.HashMap<>();
        runtimes.values().forEach(runtime -> {
            MatchPlayerId id = new MatchPlayerId(matchId, runtime.getUserId());
            MatchPlayer mp = matchPlayers.findById(id).orElse(null);
            if (mp == null) {
                log.warn("MatchPlayer missing on finalizeMatch (matchId={}, userId={})", matchId, runtime.getUserId());
                return;
            }
            mp.setLivesRemaining(Math.max(0, runtime.getLivesRemaining()));
            mp.setScore(runtime.getScore());
            mp.setCurrentStreak(runtime.getCurrentStreak());
            mp.setBestStreakInMatch(runtime.getBestStreakInMatch());
            mp.setRoundsPlayed(runtime.getRoundsPlayed());
            mp.setResult(results.getOrDefault(runtime.getUserId(), MatchResult.LOSS));
            matchPlayers.save(mp);

            Double avgDeviation = avgDeviationByUser.get(runtime.getUserId());
            statsService.recordFinishedGame(runtime.getUserId(), mode, mp, avgDeviation);
            List<AchievementResponse> achievements =
                    achievementService.evaluateAfterGame(runtime.getUserId(), mode, mp, avgDeviation);
            unlocked.put(runtime.getUserId(), achievements == null ? List.of() : achievements);
        });

        return unlocked;
    }

    public String resolveUsername(UUID userId) {
        return users.findById(userId).map(u -> u.getUsername()).orElse("?");
    }

    private static String answerGivenOf(PlayerRoundOutcome o) {
        if (o.optionGiven() != null) return o.optionGiven().toString();
        if (o.valueGiven() != null) return o.valueGiven().toPlainString();
        return null;
    }
}
