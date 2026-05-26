package com.versus.api.duel;

import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.duel.dto.AnswerMessage;
import com.versus.api.duel.dto.AnswerResultPayload;
import com.versus.api.duel.dto.EffectAppliedPayload;
import com.versus.api.duel.dto.FinalStatsPayload;
import com.versus.api.duel.dto.MatchEndPayload;
import com.versus.api.duel.dto.PlayerRoundOutcome;
import com.versus.api.duel.dto.PlayerRuntimeSnapshot;
import com.versus.api.duel.dto.QuestionPayload;
import com.versus.api.duel.dto.RoundResultPayload;
import com.versus.api.duel.dto.SabotageActivatedPayload;
import com.versus.api.duel.dto.SabotageMessage;
import com.versus.api.duel.dto.SabotageRejectedPayload;
import com.versus.api.duel.engine.DuelEngine;
import com.versus.api.duel.engine.RoundResolution;
import com.versus.api.duel.persistence.DuelPersistenceService;
import com.versus.api.duel.state.DuelMatchState;
import com.versus.api.duel.state.DuelPhase;
import com.versus.api.duel.state.DuelPlayerRuntime;
import com.versus.api.duel.state.DuelRoundState;
import com.versus.api.duel.state.PendingEffect;
import com.versus.api.duel.state.RawAnswer;
import com.versus.api.duel.state.SabotageType;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.state.LiveMatchState;
import com.versus.api.match.state.LivePlayerState;
import com.versus.api.questions.QuestionService;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.dto.QuestionBinaryResponse;
import com.versus.api.questions.dto.QuestionOptionResponse;
import com.versus.api.websocket.MatchEventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orquesta el ciclo de vida de una partida de duelo (#91 #92 #93).
 * Escucha {@link MatchStartedEvent} publicado por MatchService cuando ambos jugadores
 * marcaron ready y arrancan: crea {@link DuelMatchState}, programa primer round, etc.
 *
 * Estado in-memory: las partidas se pierden si reinicia el backend (limitación
 * conocida; futuro upgrade a Redis fuera de scope).
 */
@Component
@Slf4j
public class DuelOrchestrator {

    private static final int INITIAL_LIVES = 3;
    private static final int DEFAULT_TIMER_SECONDS = 15;
    private static final int TIME_BOMB_TIMER_SECONDS = 10;
    private static final int MAX_ROUNDS = 10;
    private static final long FIRST_ROUND_DELAY_MS = 1000;
    private static final long BETWEEN_ROUNDS_DELAY_MS = 3000;
    private static final long DISCONNECT_GRACE_MS = 10_000;

    private final SimpMessagingTemplate broker;
    private final QuestionService questions;
    private final DuelPersistenceService persistence;
    private final ScheduledExecutorService scheduler;
    private final Map<GameMode, DuelEngine> engines;
    private final Map<UUID, DuelMatchState> duels = new ConcurrentHashMap<>();

    public DuelOrchestrator(SimpMessagingTemplate broker,
                            QuestionService questions,
                            DuelPersistenceService persistence,
                            @Qualifier("duelScheduler") ScheduledExecutorService scheduler,
                            List<DuelEngine> engineList) {
        this.broker = broker;
        this.questions = questions;
        this.persistence = persistence;
        this.scheduler = scheduler;
        Map<GameMode, DuelEngine> map = new LinkedHashMap<>();
        engineList.forEach(e -> map.put(e.mode(), e));
        this.engines = Map.copyOf(map);
        log.info("DuelOrchestrator initialized with engines: {}", this.engines.keySet());
    }

    @EventListener
    public void onMatchStarted(MatchStartedEvent event) {
        LiveMatchState live = event.state();
        if (!engines.containsKey(live.getMode())) {
            log.debug("No DuelEngine registered for mode {}, skipping duel orchestration", live.getMode());
            return;
        }
        beginMatch(live);
    }

    void beginMatch(LiveMatchState live) {
        DuelMatchState duel = new DuelMatchState(live.getMatchId(), live.getMode());
        live.getPlayers().values().forEach(p -> duel.getPlayers().put(p.getUserId(),
                DuelPlayerRuntime.builder()
                        .userId(p.getUserId())
                        .username(p.getUsername())
                        .livesRemaining(INITIAL_LIVES)
                        .build()));
        duels.put(duel.getMatchId(), duel);
        persistence.initializeMatchPlayers(duel.getMatchId(), duel.getPlayers(), INITIAL_LIVES);
        log.info("Duel {} begun (mode={}, players={})",
                duel.getMatchId(), duel.getMode(), duel.getPlayers().keySet());
        scheduler.schedule(() -> safeStartNextRound(duel.getMatchId()),
                FIRST_ROUND_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void safeStartNextRound(UUID matchId) {
        try {
            startNextRound(matchId);
        } catch (Exception e) {
            log.error("Failed to start next round for match {}: {}", matchId, e.getMessage(), e);
        }
    }

    private void startNextRound(UUID matchId) {
        DuelMatchState duel = duels.get(matchId);
        if (duel == null || duel.isEnded()) return;
        DuelEngine engine = engines.get(duel.getMode());
        if (engine == null) return;

        synchronized (duel) {
            if (duel.isEnded()) return;
            int n = duel.getRoundNumber().incrementAndGet();
            if (n > MAX_ROUNDS) {
                endMatchMaxRounds(duel);
                return;
            }

            Question question;
            try {
                question = questions.findRandomActiveQuestion(engine.questionType(), null);
            } catch (ApiException ex) {
                if (ex.getCode() == ErrorCode.NOT_FOUND) {
                    log.warn("Match {} ending early: no active question of type {} available",
                            matchId, engine.questionType());
                    endMatchNoQuestion(duel);
                    return;
                }
                throw ex;
            }
            Instant now = Instant.now();

            DuelRoundState round = new DuelRoundState(n, question.getId(), now, computeDeadline(duel, now));
            consumeIncomingEffectsForRound(duel, round);
            duel.setCurrentRound(round);
            duel.setPhase(DuelPhase.ROUND_OPEN);

            Map<UUID, QuestionPayload> personalPayloads = buildPersonalPayloads(duel, round, question);
            personalPayloads.forEach((userId, payload) -> sendToUser(userId, "QUESTION", matchId, payload));
            round.getEffectsApplied().forEach((target, type) -> broadcast(matchId, "EFFECT_APPLIED",
                    new EffectAppliedPayload(type, target, n)));

            int timerSeconds = personalPayloads.values().stream()
                    .mapToInt(QuestionPayload::timerSeconds)
                    .max()
                    .orElse(DEFAULT_TIMER_SECONDS);
            round.setTimeoutTask(scheduler.schedule(
                    () -> safeCloseRound(matchId, round.getRoundNumber()),
                    timerSeconds, TimeUnit.SECONDS));

            log.info("Match {} round {} started (questionId={}, timer={}s, effects={})",
                    matchId, n, question.getId(), timerSeconds, round.getEffectsApplied());
        }
    }

    private Instant computeDeadline(DuelMatchState duel, Instant now) {
        // El deadline real será el MAYOR de los deadlines por jugador (el más permisivo)
        // para que el scheduler sólo dispare cuando ambos seguro están fuera de tiempo.
        int max = DEFAULT_TIMER_SECONDS;
        for (DuelPlayerRuntime rt : duel.getPlayers().values()) {
            int seconds = rt.getIncomingEffects().stream()
                    .anyMatch(e -> e.type() == SabotageType.TIME_BOMB)
                    ? TIME_BOMB_TIMER_SECONDS
                    : DEFAULT_TIMER_SECONDS;
            if (seconds > max) max = seconds;
        }
        return now.plusSeconds(max);
    }

    private void consumeIncomingEffectsForRound(DuelMatchState duel, DuelRoundState round) {
        duel.getPlayers().forEach((userId, rt) -> {
            if (rt.getIncomingEffects().isEmpty()) return;
            // 1 efecto activo por jugador por round (el más reciente).
            PendingEffect effect = rt.getIncomingEffects().get(rt.getIncomingEffects().size() - 1);
            round.getEffectsApplied().put(userId, effect.type());
            rt.getIncomingEffects().clear();
        });
    }

    private Map<UUID, QuestionPayload> buildPersonalPayloads(DuelMatchState duel,
                                                              DuelRoundState round,
                                                              Question question) {
        Instant now = Instant.now();
        Map<UUID, QuestionPayload> map = new LinkedHashMap<>();
        for (DuelPlayerRuntime rt : duel.getPlayers().values()) {
            SabotageType effect = round.getEffectsApplied().get(rt.getUserId());
            int timerSeconds = effect == SabotageType.TIME_BOMB ? TIME_BOMB_TIMER_SECONDS : DEFAULT_TIMER_SECONDS;
            Instant deadline = now.plusSeconds(timerSeconds);
            var response = questions.toResponse(question);
            // Ofuscación: si la pregunta es binaria y el target sufre OBFUSCATION, eliminamos
            // una opción incorrecta del payload enviado a ese jugador. Server-side se sigue
            // evaluando la respuesta real contra el catálogo completo.
            if (effect == SabotageType.OBFUSCATION && response instanceof QuestionBinaryResponse bin) {
                response = applyObfuscation(bin, question);
            }
            map.put(rt.getUserId(), new QuestionPayload(
                    round.getRoundNumber(), response, now, deadline, timerSeconds,
                    Map.copyOf(round.getEffectsApplied())));
        }
        return map;
    }

    private QuestionBinaryResponse applyObfuscation(QuestionBinaryResponse bin, Question source) {
        UUID correctId = source.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .map(o -> o.getId())
                .findFirst()
                .orElse(null);
        if (correctId == null) return bin;
        List<QuestionOptionResponse> filtered = new ArrayList<>();
        boolean removed = false;
        for (QuestionOptionResponse opt : bin.options()) {
            if (!removed && !opt.id().equals(correctId)) {
                removed = true;
                continue;
            }
            filtered.add(opt);
        }
        return new QuestionBinaryResponse(bin.id(), bin.type(), bin.text(), bin.category(),
                filtered, bin.scrapedAt());
    }

    public AnswerResultPayload submitAnswer(UUID userId, AnswerMessage msg) {
        DuelMatchState duel = duels.get(msg.matchId());
        if (duel == null || duel.isEnded()) {
            return AnswerResultPayload.rejected("MATCH_NOT_ACTIVE");
        }
        DuelEngine engine = engines.get(duel.getMode());
        if (engine == null) return AnswerResultPayload.rejected("UNSUPPORTED_MODE");

        DuelRoundState round;
        synchronized (duel) {
            round = duel.getCurrentRound();
            if (round == null || duel.getPhase() != DuelPhase.ROUND_OPEN) {
                return AnswerResultPayload.rejected("WRONG_PHASE");
            }
            if (!round.getQuestionId().equals(msg.questionId())) {
                return AnswerResultPayload.rejected("STALE");
            }
            if (!duel.getPlayers().containsKey(userId)) {
                return AnswerResultPayload.rejected("NOT_IN_MATCH");
            }
            if (round.getAnswers().containsKey(userId)) {
                return AnswerResultPayload.rejected("ALREADY_ANSWERED");
            }
            round.getAnswers().put(userId,
                    new RawAnswer(userId, msg.questionId(),
                            msg.optionId() == null ? null : msg.optionId().toString(),
                            msg.value(), Instant.now()));
        }

        if (round.getAnswers().size() >= duel.getPlayers().size()) {
            // Cancelamos el timeout y cerramos el round inmediatamente.
            var task = round.getTimeoutTask();
            if (task != null) task.cancel(false);
            // Ejecutamos en el scheduler para no bloquear el thread WS con persistencia.
            scheduler.submit(() -> safeCloseRound(duel.getMatchId(), round.getRoundNumber()));
        }
        return AnswerResultPayload.accepted(null, null);
    }

    private void safeCloseRound(UUID matchId, int roundNumber) {
        try {
            closeRound(matchId, roundNumber);
        } catch (Exception e) {
            log.error("Error closing round {} of match {}: {}", roundNumber, matchId, e.getMessage(), e);
        }
    }

    private void closeRound(UUID matchId, int roundNumber) {
        DuelMatchState duel = duels.get(matchId);
        if (duel == null || duel.isEnded()) return;
        DuelRoundState round = duel.getCurrentRound();
        if (round == null || round.getRoundNumber() != roundNumber) return;
        if (!round.getClosed().compareAndSet(false, true)) return; // idempotente

        DuelEngine engine = engines.get(duel.getMode());
        if (engine == null) return;
        Question question = questions.findActiveQuestion(round.getQuestionId(), engine.questionType());

        RoundResolution resolution;
        synchronized (duel) {
            duel.setPhase(DuelPhase.BETWEEN_ROUNDS);
            resolution = engine.resolveRound(duel, round, question);
            // Apply life deltas and stats:
            for (PlayerRoundOutcome outcome : resolution.outcomes()) {
                DuelPlayerRuntime rt = duel.getPlayers().get(outcome.userId());
                if (rt == null) continue;
                rt.setRoundsPlayed(rt.getRoundsPlayed() + 1);
                rt.setLivesRemaining(Math.max(0, rt.getLivesRemaining() + outcome.lifeDelta()));
            }
        }

        // Persistir async respecto del próximo round.
        scheduler.submit(() -> persistence.recordRound(matchId, round.getQuestionId(),
                round.getRoundNumber(), resolution.outcomes()));

        Map<UUID, PlayerRuntimeSnapshot> snapshots = snapshotRuntimes(duel);
        broadcast(matchId, "ROUND_RESULT", new RoundResultPayload(
                round.getRoundNumber(), round.getQuestionId(), resolution.reveal(),
                resolution.outcomes(), snapshots));

        boolean gameOver = duel.getPlayers().values().stream().anyMatch(p -> p.getLivesRemaining() <= 0);
        if (gameOver) {
            endMatchNormal(duel);
        } else if (duel.getRoundNumber().get() >= MAX_ROUNDS) {
            endMatchMaxRounds(duel);
        } else {
            scheduler.schedule(() -> safeStartNextRound(matchId),
                    BETWEEN_ROUNDS_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    public SabotageRejectedPayload activateSabotage(UUID userId, SabotageMessage msg) {
        DuelMatchState duel = duels.get(msg.matchId());
        if (duel == null || duel.isEnded()) {
            return new SabotageRejectedPayload(SabotageRejectedPayload.WRONG_PHASE);
        }
        if (duel.getMode() != GameMode.SABOTAGE) {
            return new SabotageRejectedPayload(SabotageRejectedPayload.UNSUPPORTED_MODE);
        }
        synchronized (duel) {
            DuelRoundState round = duel.getCurrentRound();
            if (round == null || duel.getPhase() != DuelPhase.ROUND_OPEN) {
                return new SabotageRejectedPayload(SabotageRejectedPayload.WRONG_PHASE);
            }
            DuelPlayerRuntime self = duel.getPlayers().get(userId);
            DuelPlayerRuntime target = duel.getPlayers().get(msg.targetUserId());
            if (self == null || target == null || userId.equals(msg.targetUserId())) {
                return new SabotageRejectedPayload(SabotageRejectedPayload.INVALID_TARGET);
            }
            if (self.getSabotageTokens() <= 0) {
                return new SabotageRejectedPayload(SabotageRejectedPayload.NO_TOKENS);
            }
            if (round.getSabotageUsedBy().contains(userId)) {
                return new SabotageRejectedPayload(SabotageRejectedPayload.ALREADY_USED);
            }
            if (round.getAnswers().containsKey(userId)) {
                return new SabotageRejectedPayload(SabotageRejectedPayload.WRONG_PHASE);
            }
            self.setSabotageTokens(self.getSabotageTokens() - 1);
            self.setSabotagesUsed(self.getSabotagesUsed() + 1);
            round.getSabotageUsedBy().add(userId);
            target.getIncomingEffects().add(new PendingEffect(msg.type(), userId));
            int appliesOn = duel.getRoundNumber().get() + 1;
            broadcast(duel.getMatchId(), "SABOTAGE_ACTIVATED",
                    new SabotageActivatedPayload(msg.type(), userId, msg.targetUserId(), appliesOn));
        }
        return null;
    }

    private Map<UUID, PlayerRuntimeSnapshot> snapshotRuntimes(DuelMatchState duel) {
        Map<UUID, PlayerRuntimeSnapshot> out = new LinkedHashMap<>();
        duel.getPlayers().forEach((id, rt) -> out.put(id, new PlayerRuntimeSnapshot(
                id, rt.getLivesRemaining(), rt.getScore(), rt.getCurrentStreak(),
                rt.getSabotageTokens(),
                rt.getIncomingEffects().stream().map(PendingEffect::type).toList())));
        return out;
    }

    private void endMatchNormal(DuelMatchState duel) {
        finalize(duel, MatchEndPayload.REASON_NORMAL);
    }

    private void endMatchMaxRounds(DuelMatchState duel) {
        finalize(duel, MatchEndPayload.REASON_MAX_ROUNDS_TIE);
    }

    private void endMatchNoQuestion(DuelMatchState duel) {
        finalize(duel, MatchEndPayload.REASON_NO_QUESTION);
    }

    private void endMatchDisconnect(DuelMatchState duel, UUID disconnectedUserId) {
        // Forzar al desconectado a 0 vidas para que el otro sea winner por reglas estándar.
        DuelPlayerRuntime rt = duel.getPlayers().get(disconnectedUserId);
        if (rt != null) rt.setLivesRemaining(0);
        finalize(duel, MatchEndPayload.REASON_DISCONNECT);
    }

    private void finalize(DuelMatchState duel, String reason) {
        synchronized (duel) {
            if (duel.isEnded()) return;
            duel.setEnded(true);
            duel.setPhase(DuelPhase.ENDED);
        }
        UUID winnerId = pickWinner(duel);
        Map<UUID, MatchResult> results = new HashMap<>();
        duel.getPlayers().keySet().forEach(uid -> {
            if (winnerId == null) results.put(uid, MatchResult.DRAW);
            else if (uid.equals(winnerId)) results.put(uid, MatchResult.WIN);
            else results.put(uid, MatchResult.LOSS);
        });

        Map<UUID, Double> avgDeviationByUser = new HashMap<>();
        if (duel.getMode() == GameMode.PRECISION_DUEL) {
            // PrecisionDuelEngine acumula deviationSum/deviationCount en cada runtime.
            duel.getPlayers().values().forEach(rt -> {
                if (rt.getDeviationCount() > 0) {
                    avgDeviationByUser.put(rt.getUserId(),
                            rt.getDeviationSum() / rt.getDeviationCount());
                } else {
                    avgDeviationByUser.put(rt.getUserId(), null);
                }
            });
        }

        Map<UUID, List<AchievementResponse>> unlocked;
        try {
            unlocked = persistence.finalizeMatch(duel.getMatchId(), duel.getMode(), results,
                    duel.getPlayers(), avgDeviationByUser);
        } catch (Exception e) {
            log.error("Failed to persist final match {}: {}", duel.getMatchId(), e.getMessage(), e);
            unlocked = Map.of();
        }

        List<FinalStatsPayload> finalStats = new ArrayList<>();
        for (DuelPlayerRuntime rt : duel.getPlayers().values()) {
            finalStats.add(new FinalStatsPayload(
                    rt.getUserId(), rt.getUsername(),
                    results.get(rt.getUserId()),
                    rt.getLivesRemaining(), rt.getScore(),
                    rt.getBestStreakInMatch(), rt.getRoundsPlayed(),
                    avgDeviationByUser.get(rt.getUserId()),
                    rt.getSabotagesUsed()));
        }
        broadcast(duel.getMatchId(), "MATCH_END",
                new MatchEndPayload(winnerId, reason, finalStats));
        duels.remove(duel.getMatchId());
        log.info("Match {} ended (reason={}, winner={})", duel.getMatchId(), reason, winnerId);
    }

    private UUID pickWinner(DuelMatchState duel) {
        List<DuelPlayerRuntime> alive = duel.getPlayers().values().stream()
                .filter(p -> p.getLivesRemaining() > 0)
                .toList();
        if (alive.size() == 1) return alive.get(0).getUserId();
        if (alive.isEmpty()) return null;
        // Empate por vidas → mayor score; si sigue empate → DRAW.
        DuelPlayerRuntime top = alive.get(0);
        boolean tied = false;
        for (int i = 1; i < alive.size(); i++) {
            DuelPlayerRuntime p = alive.get(i);
            if (p.getScore() > top.getScore()) { top = p; tied = false; }
            else if (p.getScore() == top.getScore()) tied = true;
        }
        return tied ? null : top.getUserId();
    }

    @EventListener
    public void onSessionDisconnect(org.springframework.web.socket.messaging.SessionDisconnectEvent e) {
        if (e.getUser() == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(e.getUser().getName());
        } catch (IllegalArgumentException ex) {
            return;
        }
        duels.values().stream()
                .filter(d -> !d.isEnded() && d.getPlayers().containsKey(userId))
                .forEach(d -> handleDisconnect(d, userId));
    }

    private void handleDisconnect(DuelMatchState duel, UUID userId) {
        DuelPlayerRuntime rt = duel.getPlayers().get(userId);
        if (rt == null || rt.isDisconnected()) return;
        rt.setDisconnected(true);
        log.info("Player {} disconnected from match {} (grace {}ms)",
                userId, duel.getMatchId(), DISCONNECT_GRACE_MS);
        scheduler.schedule(() -> {
            DuelPlayerRuntime current = duel.getPlayers().get(userId);
            if (current != null && current.isDisconnected() && !duel.isEnded()) {
                endMatchDisconnect(duel, userId);
            }
        }, DISCONNECT_GRACE_MS, TimeUnit.MILLISECONDS);
    }

    private void broadcast(UUID matchId, String type, Object payload) {
        broker.convertAndSend("/topic/match/" + matchId,
                MatchEventEnvelope.of(type, matchId, payload));
    }

    private void sendToUser(UUID userId, String type, UUID matchId, Object payload) {
        broker.convertAndSendToUser(userId.toString(), "/queue/match",
                MatchEventEnvelope.of(type, matchId, payload));
    }

    // ─── Test hooks ────────────────────────────────────────────────────────────
    Map<UUID, DuelMatchState> duelsView() { return Map.copyOf(duels); }
    DuelMatchState duelState(UUID id) { return duels.get(id); }
    void registerForTest(DuelMatchState state) { duels.put(state.getMatchId(), state); }
}
