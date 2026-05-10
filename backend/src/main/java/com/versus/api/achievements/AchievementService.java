package com.versus.api.achievements;

import com.versus.api.achievements.domain.Achievement;
import com.versus.api.achievements.domain.UserAchievement;
import com.versus.api.achievements.domain.UserAchievementId;
import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.achievements.dto.AchievementUnlockedEvent;
import com.versus.api.achievements.repo.AchievementRepository;
import com.versus.api.achievements.repo.UserAchievementRepository;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.stats.domain.PlayerStats;
import com.versus.api.stats.repo.PlayerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievements;
    private final UserAchievementRepository userAchievements;
    private final PlayerStatsRepository playerStats;
    private final SimpMessagingTemplate messaging;

    @Transactional(readOnly = true)
    public List<AchievementResponse> listForUser(UUID userId) {
        Map<UUID, UserAchievement> unlocked = userAchievements.findByIdUserId(userId).stream()
                .collect(Collectors.toMap(ua -> ua.getAchievement().getId(), Function.identity()));
        return achievements.findAll().stream()
                .sorted(Comparator.comparing(Achievement::getCategory).thenComparing(Achievement::getName))
                .map(achievement -> toResponse(achievement, unlocked.get(achievement.getId())))
                .toList();
    }

    @Transactional
    public List<AchievementResponse> evaluateAfterGame(UUID userId,
                                                       GameMode mode,
                                                       MatchPlayer matchPlayer,
                                                       Double matchAvgDeviation) {
        Set<String> keys = keysUnlockedByGame(userId, mode, matchPlayer, matchAvgDeviation);
        if (keys.isEmpty()) {
            return List.of();
        }

        Map<String, Achievement> catalog = achievements.findByKeyIn(keys).stream()
                .collect(Collectors.toMap(Achievement::getKey, Function.identity()));

        List<AchievementResponse> unlocked = keys.stream()
                .map(catalog::get)
                .filter(achievement -> achievement != null)
                .filter(achievement -> !userAchievements.existsByIdUserIdAndIdAchievementId(userId, achievement.getId()))
                .map(achievement -> unlock(userId, achievement))
                .toList();

        unlocked.forEach(achievement -> messaging.convertAndSendToUser(
                userId.toString(),
                "/queue/achievements",
                AchievementUnlockedEvent.of(achievement)));
        return unlocked;
    }

    private Set<String> keysUnlockedByGame(UUID userId,
                                           GameMode mode,
                                           MatchPlayer matchPlayer,
                                           Double matchAvgDeviation) {
        Set<String> keys = new LinkedHashSet<>();
        List<PlayerStats> stats = allStats(userId);

        int totalGames = stats.stream().mapToInt(PlayerStats::getGamesPlayed).sum();
        int totalWins = stats.stream().mapToInt(PlayerStats::getGamesWon).sum();
        int bestStreak = Math.max(
                matchPlayer.getBestStreakInMatch() == null ? 0 : matchPlayer.getBestStreakInMatch(),
                stats.stream().mapToInt(PlayerStats::getBestStreak).max().orElse(0));

        if (totalGames >= 1) keys.add(AchievementCatalog.FIRST_GAME);
        if (totalWins >= 1) keys.add(AchievementCatalog.FIRST_WIN);
        if (bestStreak >= 5) keys.add(AchievementCatalog.STREAK_5);
        if (bestStreak >= 10) keys.add(AchievementCatalog.STREAK_10);
        if (bestStreak >= 20) keys.add(AchievementCatalog.STREAK_20);

        Optional<PlayerStats> survival = stat(stats, GameMode.SURVIVAL);
        if (survival.map(PlayerStats::getGamesPlayed).orElse(0) >= 1) {
            keys.add(AchievementCatalog.SURVIVAL_FIRST_GAME);
        }
        if (mode == GameMode.SURVIVAL && safe(matchPlayer.getRoundsPlayed()) >= 10) {
            keys.add(AchievementCatalog.SURVIVAL_10_ROUNDS);
        }
        if (mode == GameMode.SURVIVAL
                && matchPlayer.getResult() == MatchResult.WIN
                && safe(matchPlayer.getLivesRemaining()) >= 3) {
            keys.add(AchievementCatalog.SURVIVAL_PERFECT_LIVES);
        }

        Optional<PlayerStats> precision = stat(stats, GameMode.PRECISION);
        if (precision.map(PlayerStats::getGamesPlayed).orElse(0) >= 1) {
            keys.add(AchievementCatalog.PRECISION_FIRST_GAME);
        }
        if (mode == GameMode.PRECISION && matchAvgDeviation != null && matchAvgDeviation <= 1.0) {
            keys.add(AchievementCatalog.PRECISION_SNIPER);
        }
        if (precision.map(PlayerStats::getGamesPlayed).orElse(0) >= 10
                && precision.map(PlayerStats::getAvgDeviation).orElse(Double.MAX_VALUE) < 5.0) {
            keys.add(AchievementCatalog.PRECISION_AVG_UNDER_5);
        }

        if (isPvp(mode) && matchPlayer.getResult() == MatchResult.WIN) {
            keys.add(AchievementCatalog.PVP_FIRST_WIN);
        }
        int pvpWins = stats.stream()
                .filter(stat -> isPvp(stat.getMode()))
                .mapToInt(PlayerStats::getGamesWon)
                .sum();
        if (pvpWins >= 10) keys.add(AchievementCatalog.PVP_10_DUELS);
        if (mode == GameMode.SABOTAGE && matchPlayer.getResult() == MatchResult.WIN) {
            keys.add(AchievementCatalog.SABOTAGE_WIN);
        }
        if (hasPlayedAllModes(stats)) {
            keys.add(AchievementCatalog.COLLECTOR_ALL_MODES);
        }
        return keys;
    }

    private AchievementResponse unlock(UUID userId, Achievement achievement) {
        UserAchievement unlocked = userAchievements.save(UserAchievement.builder()
                .id(new UserAchievementId(userId, achievement.getId()))
                .achievement(achievement)
                .unlockedAt(Instant.now())
                .build());
        return toResponse(achievement, unlocked);
    }

    private List<PlayerStats> allStats(UUID userId) {
        return Arrays.stream(GameMode.values())
                .map(mode -> playerStats.findByUserIdAndMode(userId, mode))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<PlayerStats> stat(List<PlayerStats> stats, GameMode mode) {
        return stats.stream().filter(s -> s.getMode() == mode).findFirst();
    }

    private boolean hasPlayedAllModes(List<PlayerStats> stats) {
        return Arrays.stream(GameMode.values())
                .allMatch(mode -> stat(stats, mode).map(PlayerStats::getGamesPlayed).orElse(0) > 0);
    }

    private boolean isPvp(GameMode mode) {
        return mode == GameMode.BINARY_DUEL || mode == GameMode.PRECISION_DUEL || mode == GameMode.SABOTAGE;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private AchievementResponse toResponse(Achievement achievement, UserAchievement unlocked) {
        boolean isUnlocked = unlocked != null;
        return new AchievementResponse(
                achievement.getId().toString(),
                achievement.getKey(),
                isUnlocked ? achievement.getName() : "???",
                isUnlocked ? achievement.getDescription() : "???",
                isUnlocked ? achievement.getIconKey() : "lock",
                achievement.getCategory(),
                isUnlocked,
                isUnlocked ? unlocked.getUnlockedAt() : null);
    }
}
