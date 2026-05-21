package com.versus.api.match;

import com.versus.api.common.exception.ApiException;
import com.versus.api.match.domain.Match;
import com.versus.api.match.dto.*;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.match.state.LivePlayerState;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import com.versus.api.websocket.MatchEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate broker;

    @Value("${versus.match.start-countdown-seconds:3}")
    private int countdownSeconds;

    private final Map<UUID, LiveMatchState> liveMatches = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "match-countdown");
                t.setDaemon(true);
                return t;
            });

    @Transactional
    public LiveMatchState createMatch(GameMode mode, UUID ownerUserId) {
        if (!mode.isMultiplayer()) {
            throw ApiException.validation("Cannot create multiplayer match for solo mode " + mode);
        }
        Match persisted = matchRepository.save(Match.builder()
                .mode(mode)
                .status(MatchStatus.WAITING)
                .roomCode(generateUniqueRoomCode())
                .ownerUserId(ownerUserId)
                .build());

        LiveMatchState state = LiveMatchState.builder()
                .matchId(persisted.getId())
                .mode(mode)
                .roomCode(persisted.getRoomCode())
                .createdAt(persisted.getCreatedAt())
                .build();
        liveMatches.put(persisted.getId(), state);
        log.info("Match {} created (mode={}, owner={})", persisted.getId(), mode, ownerUserId);
        return state;
    }

    public LiveMatchState addPlayer(UUID matchId, UUID userId) {
        LiveMatchState state = requireLive(matchId);
        synchronized (state) {
            if (state.getStatus() != MatchStatus.WAITING) {
                throw ApiException.conflict("Match is not accepting players");
            }
            if (state.getPlayers().containsKey(userId)) {
                return state;
            }
            if (state.isFull()) {
                throw ApiException.conflict("Match is full");
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("User not found"));
            LivePlayerState player = LivePlayerState.builder()
                    .userId(userId)
                    .username(user.getUsername())
                    .avatarUrl(user.getAvatarUrl())
                    .ready(false)
                    .build();
            state.getPlayers().put(userId, player);
            broadcast(matchId, "PLAYER_JOINED", new PlayerJoinedEvent(toDto(player)));
            log.info("User {} joined match {} ({} / {})",
                    userId, matchId, state.getPlayers().size(), state.getMode().requiredPlayers());
            return state;
        }
    }

    public void markReady(UUID matchId, UUID userId, boolean ready) {
        LiveMatchState state = requireLive(matchId);
        synchronized (state) {
            LivePlayerState player = state.getPlayers().get(userId);
            if (player == null) {
                throw ApiException.forbidden("Player not in match");
            }
            if (state.getStatus() != MatchStatus.WAITING) {
                return;
            }
            if (player.isReady() == ready) return;
            player.setReady(ready);
            broadcast(matchId, "PLAYER_READY", new PlayerReadyEvent(userId, ready));
            if (state.allReady()) {
                beginCountdown(state);
            }
        }
    }

    public void removePlayer(UUID matchId, UUID userId) {
        LiveMatchState state = liveMatches.get(matchId);
        if (state == null) return;
        synchronized (state) {
            if (state.getPlayers().remove(userId) == null) return;
            broadcast(matchId, "PLAYER_LEFT", new PlayerLeftEvent(userId));
            log.info("User {} left match {} ({} remaining)", userId, matchId, state.getPlayers().size());
            if (state.getPlayers().isEmpty() && state.getStatus() == MatchStatus.WAITING) {
                liveMatches.remove(matchId);
                matchRepository.findById(matchId).ifPresent(m -> {
                    m.setStatus(MatchStatus.FINISHED);
                    m.setFinishedAt(java.time.Instant.now());
                    matchRepository.save(m);
                });
                log.info("Match {} disposed (empty lobby)", matchId);
            }
        }
    }

    public LiveMatchState requireLive(UUID matchId) {
        LiveMatchState state = liveMatches.get(matchId);
        if (state == null) throw ApiException.notFound("Match not found or already finished");
        return state;
    }

    public LobbyStateDto toLobbyDto(LiveMatchState state) {
        synchronized (state) {
            List<PlayerInLobbyDto> players = state.getPlayers().values().stream()
                    .map(this::toDto)
                    .toList();
            return new LobbyStateDto(
                    state.getMatchId(),
                    state.getMode(),
                    state.getStatus(),
                    players,
                    state.getMode().requiredPlayers(),
                    state.getRoomCode());
        }
    }

    PlayerInLobbyDto toDto(LivePlayerState p) {
        return new PlayerInLobbyDto(p.getUserId(), p.getUsername(), p.getAvatarUrl(), p.isReady());
    }

    private void beginCountdown(LiveMatchState state) {
        broadcast(state.getMatchId(), "MATCH_STARTING", new MatchStartingEvent(countdownSeconds));
        log.info("Match {} countdown started ({}s)", state.getMatchId(), countdownSeconds);
        scheduler.schedule(() -> startMatch(state.getMatchId()),
                countdownSeconds, TimeUnit.SECONDS);
    }

    void startMatch(UUID matchId) {
        LiveMatchState state = liveMatches.get(matchId);
        if (state == null) return;
        synchronized (state) {
            if (state.getStatus() != MatchStatus.WAITING) return;
            if (!state.allReady()) return;
            state.setStatus(MatchStatus.IN_PROGRESS);
            matchRepository.findById(matchId).ifPresent(m -> {
                m.setStatus(MatchStatus.IN_PROGRESS);
                matchRepository.save(m);
            });
            broadcast(matchId, "MATCH_START", new MatchStartEvent(matchId, state.getMode()));
            log.info("Match {} started (mode={})", matchId, state.getMode());
        }
    }

    private void broadcast(UUID matchId, String type, Object payload) {
        broker.convertAndSend(
                "/topic/match/" + matchId,
                MatchEventEnvelope.of(type, matchId, payload));
    }

    public void notifyMatchFound(UUID userId, MatchFoundEvent event) {
        broker.convertAndSendToUser(
                userId.toString(),
                "/queue/match",
                MatchEventEnvelope.of("MATCH_FOUND", event.matchId(), event));
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 16; attempt++) {
            String code = randomCode();
            if (matchRepository.findByRoomCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Could not generate unique room code");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CODE_CHARS[RNG.nextInt(CODE_CHARS.length)]);
        return sb.toString();
    }

    Map<UUID, LiveMatchState> liveMatchesView() {
        return Map.copyOf(liveMatches);
    }

    void putForTest(LiveMatchState state) {
        Objects.requireNonNull(state, "state");
        liveMatches.put(state.getMatchId(), state);
    }
}
