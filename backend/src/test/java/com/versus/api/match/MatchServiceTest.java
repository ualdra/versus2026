package com.versus.api.match;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.match.domain.Match;
import com.versus.api.match.dto.PlayerInLobbyDto;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.users.Role;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import com.versus.api.websocket.MatchEventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("MatchService")
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock MatchRepository matchRepository;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate broker;

    @InjectMocks MatchService matchService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(matchService, "countdownSeconds", 1);
    }

    private User user(String name) {
        return User.builder()
                .id(UUID.randomUUID()).username(name).email(name + "@versus.com")
                .passwordHash("$2a$hash").role(Role.PLAYER).isActive(true)
                .build();
    }

    private void stubUserSave() {
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            if (m.getCreatedAt() == null) m.setCreatedAt(Instant.now());
            return m;
        });
        when(matchRepository.findByRoomCode(any())).thenReturn(Optional.empty());
    }

    @DisplayName("createMatch")
    @Nested
    class Create {

        @DisplayName("Camino feliz: persiste Match en BD y registra LiveMatchState")
        @Test
        void caminoFeliz() {
            stubUserSave();
            UUID owner = UUID.randomUUID();

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, owner);

            assertThat(state.getMatchId()).isNotNull();
            assertThat(state.getMode()).isEqualTo(GameMode.BINARY_DUEL);
            assertThat(state.getStatus()).isEqualTo(MatchStatus.WAITING);
            assertThat(state.getRoomCode()).hasSize(6);
            assertThat(matchService.liveMatchesView()).containsKey(state.getMatchId());
        }

        @DisplayName("Modo single-player se rechaza con VALIDATION_ERROR")
        @Test
        void modoSingleplayer_rechaza() {
            assertThatThrownBy(() -> matchService.createMatch(GameMode.SURVIVAL, UUID.randomUUID()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    @DisplayName("addPlayer")
    @Nested
    class AddPlayer {

        @DisplayName("Camino feliz: añade jugador y emite PLAYER_JOINED al topic")
        @Test
        void caminoFeliz() {
            stubUserSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());

            assertThat(state.getPlayers()).containsKey(u.getId());

            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("PLAYER_JOINED");
        }

        @DisplayName("Añadir el mismo usuario dos veces es idempotente y no re-emite evento")
        @Test
        void idempotente() {
            stubUserSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());

            verify(broker, times(1))
                    .convertAndSend(eq("/topic/match/" + state.getMatchId()), any(Object.class));
        }

        @DisplayName("Añadir cuando la sala está llena lanza CONFLICT")
        @Test
        void salaLlena_lanzaConflict() {
            stubUserSave();
            User u1 = user("alice");
            User u2 = user("bob");
            User u3 = user("carol");
            when(userRepository.findById(u1.getId())).thenReturn(Optional.of(u1));
            when(userRepository.findById(u2.getId())).thenReturn(Optional.of(u2));
            // u3 nunca se busca: el chequeo de "sala llena" ocurre antes que el findById.

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u1.getId());
            matchService.addPlayer(state.getMatchId(), u1.getId());
            matchService.addPlayer(state.getMatchId(), u2.getId());

            assertThatThrownBy(() -> matchService.addPlayer(state.getMatchId(), u3.getId()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.CONFLICT));
        }

        @DisplayName("Match inexistente lanza NOT_FOUND")
        @Test
        void matchInexistente_lanzaNotFound() {
            assertThatThrownBy(() -> matchService.addPlayer(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @DisplayName("Usuario inexistente lanza NOT_FOUND")
        @Test
        void usuarioInexistente_lanzaNotFound() {
            stubUserSave();
            UUID owner = UUID.randomUUID();
            UUID phantom = UUID.randomUUID();
            when(userRepository.findById(phantom)).thenReturn(Optional.empty());

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, owner);

            assertThatThrownBy(() -> matchService.addPlayer(state.getMatchId(), phantom))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @DisplayName("markReady")
    @Nested
    class MarkReady {

        @DisplayName("Cambia el estado del jugador y emite PLAYER_READY")
        @Test
        void cambiaEstado_yEmite() {
            stubUserSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());
            clearInvocations(broker);

            matchService.markReady(state.getMatchId(), u.getId(), true);

            assertThat(state.getPlayers().get(u.getId()).isReady()).isTrue();
            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("PLAYER_READY");
        }

        @DisplayName("Marcar ready cuando ya está ready no re-emite (idempotente)")
        @Test
        void idempotente() {
            stubUserSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());
            matchService.markReady(state.getMatchId(), u.getId(), true);
            clearInvocations(broker);

            matchService.markReady(state.getMatchId(), u.getId(), true);

            verifyNoInteractions(broker);
        }

        @DisplayName("Cuando todos los jugadores están listos emite MATCH_STARTING")
        @Test
        void todosListos_emiteMatchStarting() {
            stubUserSave();
            User u1 = user("alice");
            User u2 = user("bob");
            when(userRepository.findById(u1.getId())).thenReturn(Optional.of(u1));
            when(userRepository.findById(u2.getId())).thenReturn(Optional.of(u2));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u1.getId());
            matchService.addPlayer(state.getMatchId(), u1.getId());
            matchService.addPlayer(state.getMatchId(), u2.getId());
            matchService.markReady(state.getMatchId(), u1.getId(), true);
            clearInvocations(broker);

            matchService.markReady(state.getMatchId(), u2.getId(), true);

            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker, atLeast(2))
                    .convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getAllValues())
                    .extracting(MatchEventEnvelope::type)
                    .contains("PLAYER_READY", "MATCH_STARTING");
        }

        @DisplayName("Jugador que no está en la partida lanza FORBIDDEN")
        @Test
        void noEsJugador_lanzaForbidden() {
            stubUserSave();
            UUID owner = UUID.randomUUID();
            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, owner);

            assertThatThrownBy(() ->
                    matchService.markReady(state.getMatchId(), UUID.randomUUID(), true))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    @DisplayName("removePlayer")
    @Nested
    class Remove {

        @DisplayName("Quita al jugador y emite PLAYER_LEFT")
        @Test
        void quitaYEmite() {
            stubUserSave();
            User u1 = user("alice");
            User u2 = user("bob");
            when(userRepository.findById(u1.getId())).thenReturn(Optional.of(u1));
            when(userRepository.findById(u2.getId())).thenReturn(Optional.of(u2));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u1.getId());
            matchService.addPlayer(state.getMatchId(), u1.getId());
            matchService.addPlayer(state.getMatchId(), u2.getId());
            clearInvocations(broker);

            matchService.removePlayer(state.getMatchId(), u1.getId());

            assertThat(state.getPlayers()).doesNotContainKey(u1.getId());
            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSend(eq("/topic/match/" + state.getMatchId()), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("PLAYER_LEFT");
        }

        @DisplayName("Quitar al último jugador elimina la partida y la marca FINISHED en BD")
        @Test
        void ultimoJugador_eliminaPartida() {
            stubUserSave();
            User u = user("alice");
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

            LiveMatchState state = matchService.createMatch(GameMode.BINARY_DUEL, u.getId());
            matchService.addPlayer(state.getMatchId(), u.getId());

            Match persisted = Match.builder()
                    .id(state.getMatchId()).mode(GameMode.BINARY_DUEL)
                    .status(MatchStatus.WAITING).roomCode(state.getRoomCode())
                    .createdAt(Instant.now()).build();
            when(matchRepository.findById(state.getMatchId())).thenReturn(Optional.of(persisted));

            matchService.removePlayer(state.getMatchId(), u.getId());

            assertThat(matchService.liveMatchesView()).doesNotContainKey(state.getMatchId());
            assertThat(persisted.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(persisted.getFinishedAt()).isNotNull();
        }

        @DisplayName("Match inexistente no lanza nada (silencioso)")
        @Test
        void matchInexistente_silencioso() {
            assertThatCode(() -> matchService.removePlayer(UUID.randomUUID(), UUID.randomUUID()))
                    .doesNotThrowAnyException();
        }
    }

    @DisplayName("notifyMatchFound")
    @Nested
    class NotifyMatchFound {

        @DisplayName("Envía MATCH_FOUND al canal personal del usuario")
        @Test
        void enviaCanalPersonal() {
            UUID userId = UUID.randomUUID();
            UUID matchId = UUID.randomUUID();
            var event = new com.versus.api.match.dto.MatchFoundEvent(
                    matchId, GameMode.BINARY_DUEL,
                    java.util.List.of(new PlayerInLobbyDto(UUID.randomUUID(), "rival", null, false)));

            matchService.notifyMatchFound(userId, event);

            ArgumentCaptor<MatchEventEnvelope> ev = ArgumentCaptor.forClass(MatchEventEnvelope.class);
            verify(broker).convertAndSendToUser(
                    eq(userId.toString()), eq("/queue/match"), ev.capture());
            assertThat(ev.getValue().type()).isEqualTo("MATCH_FOUND");
            assertThat(ev.getValue().matchId()).isEqualTo(matchId);
        }
    }
}
