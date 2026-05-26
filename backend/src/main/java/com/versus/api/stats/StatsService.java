package com.versus.api.stats;

import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.stats.domain.PlayerStats;
import com.versus.api.stats.dto.PlayerStatsOverviewResponse;
import com.versus.api.stats.dto.PlayerStatsResponse;
import com.versus.api.stats.repo.PlayerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int SURVIVAL_WIN_ROUNDS = 5;

    private final PlayerStatsRepository playerStats;
    private final MatchRepository matches;

    @Transactional(readOnly = true)
    public PlayerStatsOverviewResponse getMine(UUID userId) {
        List<PlayerStatsResponse> byMode = Arrays.stream(GameMode.values())
                .map(mode -> toResponse(findOrEmpty(userId, mode)))
                .toList();

        String favoriteMode = byMode.stream()
                .filter(s -> s.gamesPlayed() > 0)
                .max(Comparator.comparingInt(PlayerStatsResponse::gamesPlayed))
                .map(s -> s.mode().name())
                .orElse(null);

        long totalPlayTimeSeconds = matches.sumPlayTimeSecondsByUserId(userId);

        return new PlayerStatsOverviewResponse(byMode, favoriteMode, totalPlayTimeSeconds);
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

        int score = matchPlayer.getScore();
        Integer prevAvgScore = stats.getAvgScore();
        if (prevAvgScore == null || previousGamesPlayed == 0) {
            stats.setAvgScore(score);
        } else {
            stats.setAvgScore((int) Math.round(((double) prevAvgScore * previousGamesPlayed + score) / stats.getGamesPlayed()));
        }

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
                stats.getAvgDeviation(),
                stats.getAvgScore());
    }
}
