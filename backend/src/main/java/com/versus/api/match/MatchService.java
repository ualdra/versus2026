package com.versus.api.match;

import com.versus.api.common.exception.ApiException;
import com.versus.api.match.domain.Match;
import com.versus.api.match.domain.MatchAnswer;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchRound;
import com.versus.api.match.dto.*;
import com.versus.api.match.repo.*;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.match.state.LivePlayerState;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import com.versus.api.websocket.MatchEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayers;
    private final MatchRoundRepository matchRounds;
    private final MatchAnswerRepository matchAnswers;
    private final QuestionRepository questions;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate broker;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Value("${versus.match.start-countdown-seconds:3}")
    private int countdownSeconds;

    private final Map<UUID, LiveMatchState> liveMatches = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "match-countdown");
        t.setDaemon(true);
        return t;
    });

    // ─── History & Detail ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<MatchHistoryItemResponse> getHistory(UUID userId, int page, int size, GameMode mode) {
        int clampedSize = Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(page, clampedSize);
        Page<Match> matchPage = mode != null
                ? matchRepository.findFinishedByUserIdAndMode(userId, mode.name(), pageable)
                : matchRepository.findFinishedByUserId(userId, pageable);
        return matchPage.map(match -> toHistoryItem(match, userId));
    }

    @Transactional(readOnly = true)
    public MatchDetailResponse getDetail(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));

        matchPlayers.findByIdMatchIdAndIdUserId(matchId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a player in this match"));

        List<MatchPlayer> allPlayers = matchPlayers.findByIdMatchId(matchId);
        List<UUID> playerUserIds = allPlayers.stream().map(mp -> mp.getId().getUserId()).toList();
        Map<UUID, User> userMap = userRepository.findAllById(playerUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<MatchPlayerSummary> playerSummaries = allPlayers.stream()
                .map(mp -> {
                    User u = userMap.get(mp.getId().getUserId());
                    String username = u != null ? u.getUsername() : "Unknown";
                    return new MatchPlayerSummary(mp.getId().getUserId(), username, mp.getScore(),
                            mp.getLivesRemaining(), mp.getBestStreakInMatch(), mp.getResult());
                })
                .toList();

        List<MatchRound> rounds = matchRounds.findByMatchIdOrderByRoundNumber(matchId);
        List<UUID> roundIds = rounds.stream().map(MatchRound::getId).toList();

        Map<UUID, Question> questionMap = questions.findAllById(
                        rounds.stream().map(MatchRound::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));

        List<MatchAnswer> userAnswers = matchAnswers.findByRoundIdIn(roundIds).stream()
                .filter(a -> userId.equals(a.getUserId()))
                .toList();
        Map<UUID, MatchAnswer> answerByRound = userAnswers.stream()
                .collect(Collectors.toMap(MatchAnswer::getRoundId, a -> a));

        List<RoundDetailResponse> roundDetails = rounds.stream()
                .map(round -> {
                    MatchAnswer answer = answerByRound.get(round.getId());
                    Question q = questionMap.get(round.getQuestionId());
                    String questionText = q != null ? q.getText() : "";
                    boolean correct = answer != null && Boolean.TRUE.equals(answer.getIsCorrect());
                    String answerGiven = answer != null && answer.getAnswerGiven() != null
                            ? answer.getAnswerGiven() : "";
                    Double deviation = answer != null ? answer.getDeviation() : null;
                    return new RoundDetailResponse(round.getRoundNumber(), round.getQuestionId(),
                            questionText, correct, answerGiven, deviation);
                })
                .toList();

        return new MatchDetailResponse(match.getId(), match.getMode(), match.getCreatedAt(),
                match.getFinishedAt(), playerSummaries, roundDetails);
    }

    private MatchHistoryItemResponse toHistoryItem(Match match, UUID userId) {
        MatchPlayer player = matchPlayers.findByIdMatchIdAndIdUserId(match.getId(), userId)
                .orElseThrow(() -> ApiException.notFound("Player record not found in match"));

        OpponentSummary opponent = null;
        if (isMultiplayer(match.getMode())) {
            List<MatchPlayer> allPlayers = matchPlayers.findByIdMatchId(match.getId());
            opponent = allPlayers.stream()
                    .filter(mp -> !mp.getId().getUserId().equals(userId))
                    .findFirst()
                    .flatMap(mp -> userRepository.findById(mp.getId().getUserId()))
                    .map(u -> new OpponentSummary(u.getId(), u.getUsername(), u.getAvatarUrl()))
                    .orElse(null);
        }

        return new MatchHistoryItemResponse(
                match.getId(),
                match.getMode(),
                player.getResult(),
                player.getScore(),
                player.getBestStreakInMatch(),
                player.getLivesRemaining(),
                player.getRoundsPlayed(),
                match.getFinishedAt(),
                opponent);
    }

    private boolean isMultiplayer(GameMode mode) {
        return mode == GameMode.BINARY_DUEL || mode == GameMode.PRECISION_DUEL || mode == GameMode.SABOTAGE;
    }

    // ─── Live Match Management ────────────────────────────────────────────────

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
            if (player.isReady() == ready)
                return;
            player.setReady(ready);
            broadcast(matchId, "PLAYER_READY", new PlayerReadyEvent(userId, ready));
            if (state.allReady()) {
                beginCountdown(state);
            }
        }
    }

    public void removePlayer(UUID matchId, UUID userId) {
        LiveMatchState state = liveMatches.get(matchId);
        if (state == null)
            return;
        synchronized (state) {
            if (state.getPlayers().remove(userId) == null)
                return;
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
        if (state == null)
            throw ApiException.notFound("Match not found or already finished");
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
        if (state == null)
            return;
        synchronized (state) {
            if (state.getStatus() != MatchStatus.WAITING)
                return;
            if (!state.allReady())
                return;
            state.setStatus(MatchStatus.IN_PROGRESS);
            matchRepository.findById(matchId).ifPresent(m -> {
                m.setStatus(MatchStatus.IN_PROGRESS);
                matchRepository.save(m);
            });
            broadcast(matchId, "MATCH_START", new MatchStartEvent(matchId, state.getMode()));
            log.info("Match {} started (mode={})", matchId, state.getMode());
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new com.versus.api.duel.MatchStartedEvent(state));
            }
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
            if (matchRepository.findByRoomCode(code).isEmpty())
                return code;
        }
        throw new IllegalStateException("Could not generate unique room code");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++)
            sb.append(CODE_CHARS[RNG.nextInt(CODE_CHARS.length)]);
        return sb.toString();
    }

    Map<UUID, LiveMatchState> liveMatchesView() {
        return Map.copyOf(liveMatches);
    }

    void putForTest(LiveMatchState state) {
        Objects.requireNonNull(state, "state");
        liveMatches.put(state.getMatchId(), state);
    }

    public void clearLiveMatchesForTest() {
        liveMatches.clear();
    }
}
