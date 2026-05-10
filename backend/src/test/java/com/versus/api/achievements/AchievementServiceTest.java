package com.versus.api.achievements;

import com.versus.api.achievements.domain.Achievement;
import com.versus.api.achievements.domain.UserAchievement;
import com.versus.api.achievements.domain.UserAchievementId;
import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.achievements.repo.AchievementRepository;
import com.versus.api.achievements.repo.UserAchievementRepository;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import com.versus.api.stats.domain.PlayerStats;
import com.versus.api.stats.repo.PlayerStatsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AchievementService")
@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock AchievementRepository achievements;
    @Mock UserAchievementRepository userAchievements;
    @Mock PlayerStatsRepository playerStats;
    @Mock SimpMessagingTemplate messaging;

    @InjectMocks AchievementService achievementService;

    private static final UUID USER_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID MATCH_ID = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID FIRST_GAME_ID = UUID.fromString("cccc0000-0000-0000-0000-000000000003");
    private static final UUID FIRST_WIN_ID = UUID.fromString("dddd0000-0000-0000-0000-000000000004");
    private static final UUID SURVIVAL_ID = UUID.fromString("eeee0000-0000-0000-0000-000000000005");

    @Test
    @DisplayName("listForUser oculta nombre y descripcion de logros bloqueados")
    void listForUser_hidesLockedAchievements() {
        Achievement firstGame = achievement(FIRST_GAME_ID, AchievementCatalog.FIRST_GAME, "Primeros pasos");
        Achievement firstWin = achievement(FIRST_WIN_ID, AchievementCatalog.FIRST_WIN, "Primera victoria");
        UserAchievement unlocked = unlocked(firstGame);

        when(achievements.findAll()).thenReturn(List.of(firstWin, firstGame));
        when(userAchievements.findByIdUserId(USER_ID)).thenReturn(List.of(unlocked));

        List<AchievementResponse> result = achievementService.listForUser(USER_ID);

        AchievementResponse lockedWin = result.stream()
                .filter(a -> a.key().equals(AchievementCatalog.FIRST_WIN))
                .findFirst()
                .orElseThrow();
        AchievementResponse visibleFirstGame = result.stream()
                .filter(a -> a.key().equals(AchievementCatalog.FIRST_GAME))
                .findFirst()
                .orElseThrow();

        assertThat(visibleFirstGame.unlocked()).isTrue();
        assertThat(visibleFirstGame.name()).isEqualTo("Primeros pasos");
        assertThat(lockedWin.unlocked()).isFalse();
        assertThat(lockedWin.name()).isEqualTo("???");
        assertThat(lockedWin.description()).isEqualTo("???");
        assertThat(lockedWin.iconKey()).isEqualTo("lock");
    }

    @Test
    @DisplayName("evaluateAfterGame desbloquea solo logros nuevos y emite evento")
    void evaluateAfterGame_unlocksNewAchievementsOnce() {
        Achievement firstGame = achievement(FIRST_GAME_ID, AchievementCatalog.FIRST_GAME, "Primeros pasos");
        Achievement survival = achievement(SURVIVAL_ID, AchievementCatalog.SURVIVAL_FIRST_GAME, "Superviviente");
        stubStats(GameMode.SURVIVAL, stats(GameMode.SURVIVAL, 1, 0, 0, null));
        when(achievements.findByKeyIn(any())).thenReturn(List.of(firstGame, survival));
        when(userAchievements.existsByIdUserIdAndIdAchievementId(eq(USER_ID), any())).thenReturn(false);
        when(userAchievements.save(any(UserAchievement.class))).thenAnswer(inv -> inv.getArgument(0));

        List<AchievementResponse> result = achievementService.evaluateAfterGame(
                USER_ID,
                GameMode.SURVIVAL,
                player(MatchResult.LOSS, 0, 1, 0),
                null);

        assertThat(result).extracting(AchievementResponse::key)
                .containsExactly(AchievementCatalog.FIRST_GAME, AchievementCatalog.SURVIVAL_FIRST_GAME);
        verify(userAchievements, times(2)).save(any(UserAchievement.class));
        verify(messaging, times(2)).convertAndSendToUser(eq(USER_ID.toString()), eq("/queue/achievements"), any());
    }

    @Test
    @DisplayName("evaluateAfterGame no guarda dos veces un logro ya desbloqueado")
    void evaluateAfterGame_skipsExistingAchievements() {
        Achievement firstGame = achievement(FIRST_GAME_ID, AchievementCatalog.FIRST_GAME, "Primeros pasos");
        Achievement survival = achievement(SURVIVAL_ID, AchievementCatalog.SURVIVAL_FIRST_GAME, "Superviviente");
        stubStats(GameMode.SURVIVAL, stats(GameMode.SURVIVAL, 1, 0, 0, null));
        when(achievements.findByKeyIn(any())).thenReturn(List.of(firstGame, survival));
        when(userAchievements.existsByIdUserIdAndIdAchievementId(eq(USER_ID), any())).thenReturn(true);

        List<AchievementResponse> result = achievementService.evaluateAfterGame(
                USER_ID,
                GameMode.SURVIVAL,
                player(MatchResult.LOSS, 0, 1, 0),
                null);

        assertThat(result).isEmpty();
        verify(userAchievements, never()).save(any());
        verifyNoInteractions(messaging);
    }

    private void stubStats(GameMode activeMode, PlayerStats activeStats) {
        for (GameMode mode : GameMode.values()) {
            when(playerStats.findByUserIdAndMode(USER_ID, mode))
                    .thenReturn(mode == activeMode ? Optional.of(activeStats) : Optional.empty());
        }
    }

    private Achievement achievement(UUID id, String key, String name) {
        return Achievement.builder()
                .id(id)
                .key(key)
                .name(name)
                .description("Descripcion")
                .iconKey("icon")
                .category("Primeros pasos")
                .build();
    }

    private UserAchievement unlocked(Achievement achievement) {
        return UserAchievement.builder()
                .id(new UserAchievementId(USER_ID, achievement.getId()))
                .achievement(achievement)
                .unlockedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private MatchPlayer player(MatchResult result, int bestStreak, int roundsPlayed, int livesRemaining) {
        return MatchPlayer.builder()
                .id(new MatchPlayerId(MATCH_ID, USER_ID))
                .result(result)
                .bestStreakInMatch(bestStreak)
                .roundsPlayed(roundsPlayed)
                .livesRemaining(livesRemaining)
                .build();
    }

    private PlayerStats stats(GameMode mode, int gamesPlayed, int gamesWon, int bestStreak, Double avgDeviation) {
        return PlayerStats.builder()
                .userId(USER_ID)
                .mode(mode)
                .gamesPlayed(gamesPlayed)
                .gamesWon(gamesWon)
                .bestStreak(bestStreak)
                .currentStreak(0)
                .avgDeviation(avgDeviation)
                .build();
    }
}
