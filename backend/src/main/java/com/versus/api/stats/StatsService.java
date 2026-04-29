package com.versus.api.stats;

import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.stats.domain.PlayerStats;
import com.versus.api.stats.dto.PlayerStatsResponse;
import com.versus.api.stats.repo.PlayerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int SURVIVAL_WIN_ROUNDS = 5;

    private final PlayerStatsRepository playerStats;

    @Transactional(readOnly = true)
    public List<PlayerStatsResponse> getMine(UUID userId) {
        return Arrays.stream(GameMode.values())
                .map(mode -> toResponse(findOrEmpty(userId, mode)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerStatsResponse getMine(UUID userId, GameMode mode) {
        return toResponse(findOrEmpty(userId, mode));
    }

    @Transactional
    public void recordFinishedGame(UUID userId, GameMode mode, MatchPlayer matchPlayer, Double matchAvgDeviation) {
        PlayerStats stats = playerStats.findByUserIdAndMode(userId, mode)
                .orElseGet(() -> PlayerStats.builder()
                        .userId(userId)
                        .mode(mode)
                        .gamesPlayed(0)
                        .gamesWon(0)
                        .bestStreak(0)
                        .currentStreak(0)
                        .build());

        int previousGamesPlayed = stats.getGamesPlayed();
        boolean won = isWin(mode, matchPlayer);

        stats.setGamesPlayed(previousGamesPlayed + 1);
        if (won) {
            stats.setGamesWon(stats.getGamesWon() + 1);
        }
        stats.setBestStreak(Math.max(stats.getBestStreak(), matchPlayer.getBestStreakInMatch()));
        stats.setCurrentStreak(matchPlayer.getCurrentStreak());

        if (mode == GameMode.PRECISION && matchAvgDeviation != null) {
            Double previousAvg = stats.getAvgDeviation();
            if (previousAvg == null || previousGamesPlayed == 0) {
                stats.setAvgDeviation(matchAvgDeviation);
            } else {
                stats.setAvgDeviation(((previousAvg * previousGamesPlayed) + matchAvgDeviation)
                        / stats.getGamesPlayed());
            }
        }

        playerStats.save(stats);
    }

    private boolean isWin(GameMode mode, MatchPlayer matchPlayer) {
        if (mode == GameMode.SURVIVAL) {
            return matchPlayer.getRoundsPlayed() >= SURVIVAL_WIN_ROUNDS;
        }
        return matchPlayer.getResult() == MatchResult.WIN;
    }

    private PlayerStats findOrEmpty(UUID userId, GameMode mode) {
        return playerStats.findByUserIdAndMode(userId, mode)
                .orElseGet(() -> PlayerStats.builder()
                        .userId(userId)
                        .mode(mode)
                        .gamesPlayed(0)
                        .gamesWon(0)
                        .bestStreak(0)
                        .currentStreak(0)
                        .build());
    }

    private PlayerStatsResponse toResponse(PlayerStats stats) {
        int gamesPlayed = stats.getGamesPlayed();
        double winRate = gamesPlayed == 0
                ? 0.0
                : Math.round(((double) stats.getGamesWon() / gamesPlayed * 100.0) * 10.0) / 10.0;
        return new PlayerStatsResponse(
                stats.getMode(),
                gamesPlayed,
                stats.getGamesWon(),
                winRate,
                stats.getBestStreak(),
                stats.getCurrentStreak(),
                stats.getAvgDeviation());
    }
}
