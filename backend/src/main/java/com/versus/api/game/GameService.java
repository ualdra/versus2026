package com.versus.api.game;

import com.versus.api.achievements.AchievementService;
import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.cards.CardService;
import com.versus.api.cards.domain.Card;
import com.versus.api.common.exception.ApiException;
import com.versus.api.game.dto.PrecisionAnswerRequest;
import com.versus.api.game.dto.PrecisionAnswerResponse;
import com.versus.api.game.dto.StartGameResponse;
import com.versus.api.game.dto.SurvivalAnswerRequest;
import com.versus.api.game.dto.SurvivalAnswerResponse;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.MatchStatus;
import com.versus.api.match.domain.Match;
import com.versus.api.match.domain.MatchAnswer;
import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import com.versus.api.match.domain.MatchRound;
import com.versus.api.match.repo.MatchAnswerRepository;
import com.versus.api.match.repo.MatchPlayerRepository;
import com.versus.api.match.repo.MatchRepository;
import com.versus.api.match.repo.MatchRoundRepository;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.dto.QuestionBinaryResponse;
import com.versus.api.questions.dto.QuestionNumericResponse;
import com.versus.api.questions.dto.QuestionOptionResponse;
import com.versus.api.questions.dto.QuestionResponse;
import com.versus.api.stats.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameService {

    private static final int SURVIVAL_INITIAL_LIVES = 3;
    private static final int PRECISION_INITIAL_LIVES = 100;
    private static final MathContext PRECISION_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    private final MatchRepository matches;
    private final MatchPlayerRepository matchPlayers;
    private final MatchRoundRepository matchRounds;
    private final MatchAnswerRepository matchAnswers;
    private final CardService cards;
    private final StatsService statsService;
    private final AchievementService achievementService;

    @Transactional
    public StartGameResponse startSurvival(UUID userId) {
        Match match = createMatch(userId, GameMode.SURVIVAL);
        MatchPlayer player = createPlayer(match.getId(), userId, SURVIVAL_INITIAL_LIVES);

        CardService.CardPair pair = cards.getRandomPairForSurvival();
        UUID roundToken = UUID.randomUUID();
        player.setCurrentCardAId(pair.a().getId());
        player.setCurrentCardBId(pair.b().getId());
        player.setCurrentRoundToken(roundToken);
        matchPlayers.save(player);

        return new StartGameResponse(match.getId(), buildBinaryResponse(roundToken, pair.a(), pair.b()));
    }

    @Transactional
    public SurvivalAnswerResponse answerSurvival(UUID userId, SurvivalAnswerRequest request) {
        Session session = loadSession(userId, request.sessionId(), GameMode.SURVIVAL);
        MatchPlayer player = session.player();

        if (!request.questionId().equals(player.getCurrentRoundToken())) {
            throw ApiException.validation("Invalid question token (anti-replay)");
        }

        Card cardA = cards.getById(player.getCurrentCardAId());
        Card cardB = cards.getById(player.getCurrentCardBId());

        UUID optionId = request.optionId();
        if (!optionId.equals(cardA.getId()) && !optionId.equals(cardB.getId())) {
            throw ApiException.validation("Option does not belong to current question");
        }

        Card winner = cardA.isInverse()
                ? (cardA.getValor().compareTo(cardB.getValor()) <= 0 ? cardA : cardB)
                : (cardA.getValor().compareTo(cardB.getValor()) >= 0 ? cardA : cardB);

        boolean correct = optionId.equals(winner.getId());
        int lifeDelta = correct ? 0 : -1;
        int scoreDelta = 0;

        player.setRoundsPlayed(player.getRoundsPlayed() + 1);
        if (correct) {
            player.setCurrentStreak(player.getCurrentStreak() + 1);
            player.setBestStreakInMatch(Math.max(player.getBestStreakInMatch(), player.getCurrentStreak()));
            scoreDelta = player.getCurrentStreak() * 50;
            player.setScore(player.getScore() + scoreDelta);
        } else {
            player.setCurrentStreak(0);
            player.setLivesRemaining(Math.max(0, player.getLivesRemaining() + lifeDelta));
        }

        MatchRound round = createCardRound(session.match(), cardA.getId(), cardB.getId());
        createAnswer(round, userId, optionId.toString(), null, lifeDelta, correct);

        Map<String, Number> revealedValues = Map.of(
                cardA.getId().toString(), cardA.getValor(),
                cardB.getId().toString(), cardB.getValor()
        );

        boolean gameOver = player.getLivesRemaining() == 0;
        QuestionResponse nextQuestion = null;
        List<AchievementResponse> achievementsUnlocked = List.of();
        if (gameOver) {
            finishMatch(session.match(), player, player.getRoundsPlayed() >= 5 ? MatchResult.WIN : MatchResult.LOSS);
            statsService.recordFinishedGame(userId, GameMode.SURVIVAL, player, null);
            achievementsUnlocked = safeAchievements(achievementService.evaluateAfterGame(userId, GameMode.SURVIVAL, player, null));
            player.setCurrentCardAId(null);
            player.setCurrentCardBId(null);
            player.setCurrentRoundToken(null);
        } else {
            CardService.CardPair nextPair = cards.getRandomPairForSurvival();
            UUID nextToken = UUID.randomUUID();
            player.setCurrentCardAId(nextPair.a().getId());
            player.setCurrentCardBId(nextPair.b().getId());
            player.setCurrentRoundToken(nextToken);
            nextQuestion = buildBinaryResponse(nextToken, nextPair.a(), nextPair.b());
        }

        matchPlayers.save(player);
        return new SurvivalAnswerResponse(
                correct,
                player.getLivesRemaining(),
                lifeDelta,
                player.getCurrentStreak(),
                scoreDelta,
                nextQuestion,
                gameOver,
                achievementsUnlocked,
                revealedValues);
    }

    @Transactional
    public StartGameResponse startPrecision(UUID userId) {
        Match match = createMatch(userId, GameMode.PRECISION);
        MatchPlayer player = createPlayer(match.getId(), userId, PRECISION_INITIAL_LIVES);

        Card card = cards.getRandomCard();
        UUID roundToken = UUID.randomUUID();
        player.setCurrentCardAId(card.getId());
        player.setCurrentCardBId(null);
        player.setCurrentRoundToken(roundToken);
        matchPlayers.save(player);

        return new StartGameResponse(match.getId(), buildNumericResponse(roundToken, card));
    }

    @Transactional
    public PrecisionAnswerResponse answerPrecision(UUID userId, PrecisionAnswerRequest request) {
        Session session = loadSession(userId, request.sessionId(), GameMode.PRECISION);
        MatchPlayer player = session.player();

        if (!request.questionId().equals(player.getCurrentRoundToken())) {
            throw ApiException.validation("Invalid question token (anti-replay)");
        }

        Card card = cards.getById(player.getCurrentCardAId());
        BigDecimal correctValue = card.getValor();
        if (correctValue == null || BigDecimal.ZERO.compareTo(correctValue) == 0) {
            throw ApiException.validation("Card has invalid value (zero or null)");
        }

        BigDecimal tolerance = new BigDecimal("5");

        BigDecimal deviationPercent = request.value()
                .subtract(correctValue)
                .abs()
                .divide(correctValue.abs(), PRECISION_CONTEXT)
                .multiply(new BigDecimal("100"));

        // TODO(#59): confirmar formula con el equipo.
        int lifeDelta;
        boolean correct;
        if (deviationPercent.compareTo(tolerance) <= 0) {
            lifeDelta = 5;
            correct = true;
        } else if (deviationPercent.compareTo(tolerance.multiply(new BigDecimal("2"))) <= 0) {
            lifeDelta = 0;
            correct = false;
        } else {
            lifeDelta = -Math.min(50, deviationPercent.setScale(0, RoundingMode.HALF_UP).intValue());
            correct = false;
        }

        player.setRoundsPlayed(player.getRoundsPlayed() + 1);
        player.setLivesRemaining(Math.max(0, player.getLivesRemaining() + lifeDelta));
        if (correct) {
            player.setCurrentStreak(player.getCurrentStreak() + 1);
            player.setBestStreakInMatch(Math.max(player.getBestStreakInMatch(), player.getCurrentStreak()));
        } else {
            player.setCurrentStreak(0);
        }

        MatchRound round = createCardRound(session.match(), card.getId(), null);
        createAnswer(round, userId, request.value().toPlainString(), deviationPercent.doubleValue(), lifeDelta, correct);

        boolean gameOver = player.getLivesRemaining() == 0;
        QuestionResponse nextQuestion = null;
        List<AchievementResponse> achievementsUnlocked = List.of();
        if (gameOver) {
            finishMatch(session.match(), player, MatchResult.LOSS);
            Double avgDeviation = averageDeviation(session.match().getId(), userId);
            statsService.recordFinishedGame(userId, GameMode.PRECISION, player, avgDeviation);
            achievementsUnlocked = safeAchievements(achievementService.evaluateAfterGame(
                    userId, GameMode.PRECISION, player, avgDeviation));
            player.setCurrentCardAId(null);
            player.setCurrentRoundToken(null);
        } else {
            Card nextCard = cards.getRandomCard();
            UUID nextToken = UUID.randomUUID();
            player.setCurrentCardAId(nextCard.getId());
            player.setCurrentRoundToken(nextToken);
            nextQuestion = buildNumericResponse(nextToken, nextCard);
        }

        matchPlayers.save(player);
        double roundedDeviation = deviationPercent.setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new PrecisionAnswerResponse(
                correctValue,
                roundedDeviation,
                roundedDeviation,
                lifeDelta,
                player.getLivesRemaining(),
                nextQuestion,
                gameOver,
                achievementsUnlocked);
    }

    private Match createMatch(UUID userId, GameMode mode) {
        return matches.save(Match.builder()
                .mode(mode)
                .status(MatchStatus.IN_PROGRESS)
                .ownerUserId(userId)
                .build());
    }

    private MatchPlayer createPlayer(UUID matchId, UUID userId, int lives) {
        MatchPlayer player = MatchPlayer.builder()
                .id(new MatchPlayerId(matchId, userId))
                .livesRemaining(lives)
                .score(0)
                .currentStreak(0)
                .bestStreakInMatch(0)
                .roundsPlayed(0)
                .build();
        return matchPlayers.save(player);
    }

    private Session loadSession(UUID userId, UUID sessionId, GameMode expectedMode) {
        Match match = matches.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Game session not found"));
        if (!userId.equals(match.getOwnerUserId())) {
            throw ApiException.forbidden("Game session belongs to another user");
        }
        if (match.getMode() != expectedMode) {
            throw ApiException.validation("Game session mode does not match endpoint");
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw ApiException.conflict("Game session is not in progress");
        }
        MatchPlayer player = matchPlayers.findById(new MatchPlayerId(sessionId, userId))
                .orElseThrow(() -> ApiException.notFound("Game player not found"));
        return new Session(match, player);
    }

    private MatchRound createCardRound(Match match, UUID cardAId, UUID cardBId) {
        long existingRounds = matchRounds.countByMatchId(match.getId());
        return matchRounds.save(MatchRound.builder()
                .matchId(match.getId())
                .roundNumber((int) existingRounds + 1)
                .cardAId(cardAId)
                .cardBId(cardBId)
                .build());
    }

    private void createAnswer(MatchRound round,
                              UUID userId,
                              String answerGiven,
                              Double deviation,
                              int lifeDelta,
                              boolean correct) {
        matchAnswers.save(MatchAnswer.builder()
                .roundId(round.getId())
                .userId(userId)
                .answerGiven(answerGiven)
                .deviation(deviation)
                .lifeDelta(lifeDelta)
                .isCorrect(correct)
                .build());
    }

    private void finishMatch(Match match, MatchPlayer player, MatchResult result) {
        match.setStatus(MatchStatus.FINISHED);
        match.setFinishedAt(Instant.now());
        player.setResult(result);
        matches.save(match);
    }

    private QuestionBinaryResponse buildBinaryResponse(UUID roundToken, Card a, Card b) {
        String text = a.isInverse()
                ? "¿Cuál tiene el valor MÁS BAJO?"
                : "¿Cuál tiene el valor MÁS ALTO?";
        String category = a.getCategoria() + " · " + a.getSubcategoria();
        List<QuestionOptionResponse> options = List.of(
                new QuestionOptionResponse(a.getId(), a.getNombre(), null, a.getUnidad()),
                new QuestionOptionResponse(b.getId(), b.getNombre(), null, b.getUnidad())
        );
        return new QuestionBinaryResponse(roundToken, QuestionType.BINARY, text, category, options, a.getScrapedAt());
    }

    private QuestionNumericResponse buildNumericResponse(UUID roundToken, Card card) {
        String text = "¿Cuál es el valor de " + card.getNombre() + "?";
        String category = card.getCategoria() + " · " + card.getSubcategoria();
        return new QuestionNumericResponse(roundToken, QuestionType.NUMERIC, text, category, card.getUnidad(), card.getScrapedAt());
    }

    private Double averageDeviation(UUID matchId, UUID userId) {
        List<UUID> roundIds = matchRounds.findByMatchIdOrderByRoundNumber(matchId).stream()
                .map(MatchRound::getId)
                .toList();
        return matchAnswers.findByRoundIdIn(roundIds).stream()
                .filter(answer -> userId.equals(answer.getUserId()))
                .map(MatchAnswer::getDeviation)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private List<AchievementResponse> safeAchievements(List<AchievementResponse> unlocked) {
        return unlocked == null ? List.of() : unlocked;
    }

    private record Session(Match match, MatchPlayer player) {}
}
