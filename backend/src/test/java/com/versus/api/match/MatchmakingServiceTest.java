package com.versus.api.match;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.match.domain.MatchmakingQueue;
import com.versus.api.match.dto.MatchFoundEvent;
import com.versus.api.match.dto.PlayerInLobbyDto;
import com.versus.api.match.repo.MatchmakingQueueRepository;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.match.state.LivePlayerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("MatchmakingService")
@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

    @Mock MatchmakingQueueRepository queueRepository;
    @Mock MatchService matchService;

    @InjectMocks MatchmakingService matchmakingService;

    private MatchmakingQueue entry(UUID userId, GameMode mode) {
        return MatchmakingQueue.builder()
                .id(UUID.randomUUID()).userId(userId).mode(mode)
                .enteredAt(Instant.now()).build();
    }

    private LiveMatchState liveStateWithPlayers(GameMode mode, UUID... userIds) {
        LiveMatchState s = LiveMatchState.builder()
                .matchId(UUID.randomUUID()).mode(mode).roomCode("ABC123")
                .createdAt(Instant.now()).build();
        for (UUID id : userIds) {
            s.getPlayers().put(id, LivePlayerState.builder()
                    .userId(id).username("u" + id.toString().substring(0, 4))
                    .ready(false).build());
        }
        return s;
    }

    @DisplayName("joinQueue")
    @Nested
    class JoinQueue {

        @DisplayName("Camino feliz: persiste entrada en cola")
        @Test
        void caminoFeliz() {
            UUID userId = UUID.randomUUID();
            when(queueRepository.findByUserId(userId)).thenReturn(Optional.empty());

            matchmakingService.joinQueue(userId, GameMode.BINARY_DUEL);

            ArgumentCaptor<MatchmakingQueue> captor = ArgumentCaptor.forClass(MatchmakingQueue.class);
            verify(queueRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
            assertThat(captor.getValue().getMode()).isEqualTo(GameMode.BINARY_DUEL);
        }

        @DisplayName("Modo single-player se rechaza")
        @Test
        void modoSingleplayer_rechaza() {
            assertThatThrownBy(() ->
                    matchmakingService.joinQueue(UUID.randomUUID(), GameMode.SURVIVAL))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @DisplayName("Si ya está en cola con el mismo modo no duplica")
        @Test
        void mismoModo_noDuplica() {
            UUID userId = UUID.randomUUID();
            when(queueRepository.findByUserId(userId))
                    .thenReturn(Optional.of(entry(userId, GameMode.BINARY_DUEL)));

            matchmakingService.joinQueue(userId, GameMode.BINARY_DUEL);

            verify(queueRepository, never()).save(any());
        }

        @DisplayName("Si ya está en cola con otro modo, sustituye la entrada")
        @Test
        void otroModo_sustituye() {
            UUID userId = UUID.randomUUID();
            MatchmakingQueue oldEntry = entry(userId, GameMode.BINARY_DUEL);
            when(queueRepository.findByUserId(userId))
                    .thenReturn(Optional.of(oldEntry))
                    .thenReturn(Optional.empty());

            matchmakingService.joinQueue(userId, GameMode.PRECISION_DUEL);

            verify(queueRepository).delete(oldEntry);
            verify(queueRepository).save(argThat(q -> q.getMode() == GameMode.PRECISION_DUEL));
        }
    }

    @DisplayName("leaveQueue")
    @Nested
    class LeaveQueue {

        @DisplayName("Borra la entrada del usuario")
        @Test
        void borra() {
            UUID userId = UUID.randomUUID();
            matchmakingService.leaveQueue(userId);
            verify(queueRepository).deleteByUserId(userId);
        }
    }

    @DisplayName("pollAndMatch")
    @Nested
    class PollAndMatch {

        @DisplayName("Con 2 jugadores en cola del mismo modo crea Match y notifica a ambos")
        @Test
        void dosJugadores_crearonYNotifica() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            when(queueRepository.findByModeOrderByEnteredAtAsc(GameMode.BINARY_DUEL))
                    .thenReturn(List.of(entry(a, GameMode.BINARY_DUEL),
                                        entry(b, GameMode.BINARY_DUEL)));
            for (GameMode m : GameMode.values()) {
                if (m != GameMode.BINARY_DUEL && m.isMultiplayer()) {
                    when(queueRepository.findByModeOrderByEnteredAtAsc(m)).thenReturn(List.of());
                }
            }
            LiveMatchState state = liveStateWithPlayers(GameMode.BINARY_DUEL, a, b);
            when(matchService.createMatch(GameMode.BINARY_DUEL, a)).thenReturn(state);
            when(matchService.toDto(any())).thenAnswer(inv -> {
                LivePlayerState p = inv.getArgument(0);
                return new PlayerInLobbyDto(p.getUserId(), p.getUsername(), null, false);
            });

            matchmakingService.pollAndMatch();

            verify(matchService).createMatch(GameMode.BINARY_DUEL, a);
            verify(matchService).addPlayer(state.getMatchId(), a);
            verify(matchService).addPlayer(state.getMatchId(), b);
            verify(matchService).notifyMatchFound(eq(a), any(MatchFoundEvent.class));
            verify(matchService).notifyMatchFound(eq(b), any(MatchFoundEvent.class));
            verify(queueRepository).deleteAll(any());
        }

        @DisplayName("Con menos jugadores de los necesarios no hace nada")
        @Test
        void unJugador_noEmpareja() {
            UUID a = UUID.randomUUID();
            for (GameMode m : GameMode.values()) {
                if (!m.isMultiplayer()) continue;
                when(queueRepository.findByModeOrderByEnteredAtAsc(m))
                        .thenReturn(m == GameMode.BINARY_DUEL
                                ? List.of(entry(a, GameMode.BINARY_DUEL))
                                : List.of());
            }

            matchmakingService.pollAndMatch();

            verify(matchService, never()).createMatch(any(), any());
            verify(queueRepository, never()).deleteAll(any());
        }

        @DisplayName("Con 4 jugadores del mismo modo crea 2 partidas")
        @Test
        void cuatroJugadores_dosPartidas() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            UUID d = UUID.randomUUID();
            when(queueRepository.findByModeOrderByEnteredAtAsc(GameMode.BINARY_DUEL))
                    .thenReturn(List.of(entry(a, GameMode.BINARY_DUEL),
                                        entry(b, GameMode.BINARY_DUEL),
                                        entry(c, GameMode.BINARY_DUEL),
                                        entry(d, GameMode.BINARY_DUEL)));
            for (GameMode m : GameMode.values()) {
                if (m != GameMode.BINARY_DUEL && m.isMultiplayer()) {
                    when(queueRepository.findByModeOrderByEnteredAtAsc(m)).thenReturn(List.of());
                }
            }
            when(matchService.createMatch(eq(GameMode.BINARY_DUEL), any()))
                    .thenAnswer(inv -> liveStateWithPlayers(GameMode.BINARY_DUEL,
                            (UUID) inv.getArgument(1)));

            matchmakingService.pollAndMatch();

            verify(matchService, times(2)).createMatch(eq(GameMode.BINARY_DUEL), any());
        }
    }
}
