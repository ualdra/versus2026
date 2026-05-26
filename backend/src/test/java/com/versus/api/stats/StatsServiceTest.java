package com.versus.api.stats;

import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.stats.domain.PlayerStats;
import com.versus.api.stats.dto.PlayerStatsOverviewResponse;
import com.versus.api.stats.dto.PlayerStatsResponse;
import com.versus.api.stats.repo.PlayerStatsRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("StatsService")
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock PlayerStatsRepository playerStats;
    @Mock MatchRepository matches;
    @InjectMocks StatsService statsService;

    private static final UUID USER_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");

    private MatchPlayer matchPlayer(int roundsPlayed, int streak, MatchResult result) {
        return MatchPlayer.builder()
                .id(new MatchPlayerId(UUID.randomUUID(), USER_ID))
                .livesRemaining(0).score(0)
                .currentStreak(streak).bestStreakInMatch(streak)
                .roundsPlayed(roundsPlayed).result(result)
                .build();
    }

    private PlayerStats existingStats(int gamesPlayed, int gamesWon, int bestStreak, Double avgDev) {
        return PlayerStats.builder()
                .userId(USER_ID).mode(GameMode.PRECISION)
                .gamesPlayed(gamesPlayed).gamesWon(gamesWon)
                .bestStreak(bestStreak).currentStreak(0).avgDeviation(avgDev)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getMine
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("getMine")
    @Nested
    class GetMine {

        @DisplayName("Devuelve una entrada por cada GameMode")
        @Test
        void devuelveEntryPorCadaGameMode() {
            when(playerStats.findByUserIdAndMode(any(), any())).thenReturn(Optional.empty());
            PlayerStatsOverviewResponse overview = statsService.getMine(USER_ID);
            assertThat(overview.byMode()).hasSize(GameMode.values().length);
        }

        @DisplayName("Sin stats previas los valores son cero")
        @Test
        void sinStats_valoresEnCero() {
            when(playerStats.findByUserIdAndMode(any(), any())).thenReturn(Optional.empty());
            List<PlayerStatsResponse> list = statsService.getMine(USER_ID).byMode();
            assertThat(list).allSatisfy(s -> {
                assertThat(s.gamesPlayed()).isZero();
                assertThat(s.gamesWon()).isZero();
                assertThat(s.winRate()).isZero();
            });
        }

        @DisplayName("getMine por modo devuelve stats del modo indicado")
        @Test
        void porModo_devuelveStatsDelModo() {
            PlayerStats stats = PlayerStats.builder().userId(USER_ID).mode(GameMode.SURVIVAL)
                    .gamesPlayed(5).gamesWon(3).bestStreak(4).currentStreak(1).build();
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.of(stats));
            PlayerStatsResponse res = statsService.getMine(USER_ID, GameMode.SURVIVAL);
            assertThat(res.gamesPlayed()).isEqualTo(5);
            assertThat(res.gamesWon()).isEqualTo(3);
            assertThat(res.mode()).isEqualTo(GameMode.SURVIVAL);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // recordFinishedGame
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("recordFinishedGame")
    @Nested
    class RecordFinishedGame {

        @DisplayName("Primera partida: crea stats nuevas con gamesPlayed=1")
        @Test
        void primeraPartida_creaStatsNuevas() {
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.empty());
            statsService.recordFinishedGame(USER_ID, GameMode.SURVIVAL,
                    matchPlayer(3, 0, MatchResult.LOSS), null);
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getGamesPlayed()).isEqualTo(1);
        }

        @DisplayName("Partida ganada en SURVIVAL (roundsPlayed>=5) incrementa gamesWon")
        @Test
        void survivalWin_incrementaGamesWon() {
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.empty());
            statsService.recordFinishedGame(USER_ID, GameMode.SURVIVAL,
                    matchPlayer(5, 0, null), null); // 5 rondas → WIN
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getGamesWon()).isEqualTo(1);
        }

        @DisplayName("Partida perdida en SURVIVAL (roundsPlayed<5) no incrementa gamesWon")
        @Test
        void survivalLoss_noIncrementaGamesWon() {
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.empty());
            statsService.recordFinishedGame(USER_ID, GameMode.SURVIVAL,
                    matchPlayer(4, 0, null), null); // 4 rondas → LOSS
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getGamesWon()).isZero();
        }

        @DisplayName("Actualiza bestStreak si el de la partida es mayor")
        @Test
        void actualizaBestStreakSiEsMejor() {
            PlayerStats prev = existingStats(1, 0, 2, null);
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.PRECISION)).thenReturn(Optional.of(prev));
            statsService.recordFinishedGame(USER_ID, GameMode.PRECISION,
                    matchPlayer(1, 5, MatchResult.LOSS), null); // streak 5 > bestStreak 2
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getBestStreak()).isEqualTo(5);
        }

        @DisplayName("No cambia bestStreak si el actual es mayor")
        @Test
        void noCambiaBestStreakSiActualEsMayor() {
            PlayerStats prev = existingStats(1, 0, 10, null);
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.PRECISION)).thenReturn(Optional.of(prev));
            statsService.recordFinishedGame(USER_ID, GameMode.PRECISION,
                    matchPlayer(1, 3, MatchResult.LOSS), null); // streak 3 < bestStreak 10
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getBestStreak()).isEqualTo(10);
        }

        @DisplayName("currentStreak se actualiza con el streak de la partida")
        @Test
        void actualizaCurrentStreak() {
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.empty());
            statsService.recordFinishedGame(USER_ID, GameMode.SURVIVAL,
                    matchPlayer(3, 7, null), null);
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getCurrentStreak()).isEqualTo(7);
        }

        @DisplayName("Primera partida PRECISION inicializa avgDeviation directamente")
        @Test
        void primeraPartidaPrecision_inicializaAvgDeviation() {
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.PRECISION)).thenReturn(Optional.empty());
            statsService.recordFinishedGame(USER_ID, GameMode.PRECISION,
                    matchPlayer(1, 0, MatchResult.LOSS), 15.0);
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getAvgDeviation()).isEqualTo(15.0);
        }

        @DisplayName("Segunda partida PRECISION calcula promedio rodante")
        @Test
        void segundaPartidaPrecision_promedioRodante() {
            // prev: 1 partida, avgDeviation=10.0 → nueva: 20.0 → resultado (10*1+20)/2 = 15.0
            PlayerStats prev = existingStats(1, 0, 0, 10.0);
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.PRECISION)).thenReturn(Optional.of(prev));
            statsService.recordFinishedGame(USER_ID, GameMode.PRECISION,
                    matchPlayer(1, 0, MatchResult.LOSS), 20.0);
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getAvgDeviation()).isEqualTo(15.0);
        }

        @DisplayName("SURVIVAL no actualiza avgDeviation aunque se pase null")
        @Test
        void survival_noActualizaAvgDeviation() {
            PlayerStats prev = existingStats(2, 1, 3, null);
            prev.setMode(GameMode.SURVIVAL);
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.of(prev));
            statsService.recordFinishedGame(USER_ID, GameMode.SURVIVAL,
                    matchPlayer(5, 0, null), null);
            ArgumentCaptor<PlayerStats> cap = ArgumentCaptor.forClass(PlayerStats.class);
            verify(playerStats).save(cap.capture());
            assertThat(cap.getValue().getAvgDeviation()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // toResponse — winRate
    // ═══════════════════════════════════════════════════════════════════════

    @DisplayName("winRate en toResponse")
    @Nested
    class WinRate {

        @DisplayName("0 partidas → winRate=0.0")
        @Test
        void ceroPartidas_winRateCero() {
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.empty());
            PlayerStatsResponse res = statsService.getMine(USER_ID, GameMode.SURVIVAL);
            assertThat(res.winRate()).isEqualTo(0.0);
        }

        @DisplayName("1 victoria en 2 partidas → winRate=50.0")
        @Test
        void unaVictoriaEnDosPartidas_winRate50() {
            PlayerStats stats = PlayerStats.builder().userId(USER_ID).mode(GameMode.SURVIVAL)
                    .gamesPlayed(2).gamesWon(1).bestStreak(0).currentStreak(0).build();
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.of(stats));
            PlayerStatsResponse res = statsService.getMine(USER_ID, GameMode.SURVIVAL);
            assertThat(res.winRate()).isEqualTo(50.0);
        }

        @DisplayName("1 victoria en 3 partidas → winRate=33.3")
        @Test
        void unaVictoriaEnTresPartidas_winRate33punto3() {
            PlayerStats stats = PlayerStats.builder().userId(USER_ID).mode(GameMode.SURVIVAL)
                    .gamesPlayed(3).gamesWon(1).bestStreak(0).currentStreak(0).build();
            when(playerStats.findByUserIdAndMode(USER_ID, GameMode.SURVIVAL)).thenReturn(Optional.of(stats));
            PlayerStatsResponse res = statsService.getMine(USER_ID, GameMode.SURVIVAL);
            assertThat(res.winRate()).isEqualTo(33.3);
        }
    }
}
