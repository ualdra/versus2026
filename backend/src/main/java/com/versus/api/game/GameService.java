package com.versus.api.game;

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
import com.versus.api.questions.QuestionService;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.dto.QuestionResponse;
import com.versus.api.stats.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final QuestionService questions;
    private final StatsService statsService;

    @Transactional
    public StartGameResponse startSurvival(UUID userId) {
        return start(userId, GameMode.SURVIVAL, SURVIVAL_INITIAL_LIVES, QuestionType.BINARY);
    }

    @Transactional
    public SurvivalAnswerResponse answerSurvival(UUID userId, SurvivalAnswerRequest request) {
        Session session = loadSession(userId, request.sessionId(), GameMode.SURVIVAL);
        Question question = questions.findActiveQuestion(request.questionId(), QuestionType.BINARY);
        QuestionOption selected = question.getOptions().stream()
                .filter(option -> option.getId().equals(request.optionId()))
                .findFirst()
                .orElseThrow(() -> ApiException.validation("Option does not belong to question"));

        boolean correct = Boolean.TRUE.equals(selected.getIsCorrect());
        int lifeDelta = correct ? 0 : -1;
        int scoreDelta = 0;

        MatchPlayer player = session.player();
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

        MatchRound round = createRound(session.match(), question);
        createAnswer(round, userId, selected.getId().toString(), null, lifeDelta, correct);

        boolean gameOver = player.getLivesRemaining() == 0;
        QuestionResponse nextQuestion = null;
        if (gameOver) {
            finishMatch(session.match(), player, player.getRoundsPlayed() >= 5 ? MatchResult.WIN : MatchResult.LOSS);
            statsService.recordFinishedGame(userId, GameMode.SURVIVAL, player, null);
        } else {
            nextQuestion = questions.toResponse(questions.findRandomActiveQuestion(QuestionType.BINARY, null));
        }

        matchPlayers.save(player);
        return new SurvivalAnswerResponse(
                correct,
                player.getLivesRemaining(),
                lifeDelta,
                player.getCurrentStreak(),
                scoreDelta,
                nextQuestion,
                gameOver);
    }

    @Transactional
    public StartGameResponse startPrecision(UUID userId) {
        return start(userId, GameMode.PRECISION, PRECISION_INITIAL_LIVES, QuestionType.NUMERIC);
    }

    @Transactional
    public PrecisionAnswerResponse answerPrecision(UUID userId, PrecisionAnswerRequest request) {
        Session session = loadSession(userId, request.sessionId(), GameMode.PRECISION);
        Question question = questions.findActiveQuestion(request.questionId(), QuestionType.NUMERIC);
        BigDecimal correctValue = question.getCorrectValue();
        if (correctValue == null || BigDecimal.ZERO.compareTo(correctValue) == 0) {
            throw ApiException.validation("Numeric question has invalid correct value");
        }

        BigDecimal tolerance = question.getTolerancePercent() == null
                ? new BigDecimal("5")
                : question.getTolerancePercent();

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

        MatchPlayer player = session.player();
        player.setRoundsPlayed(player.getRoundsPlayed() + 1);
        player.setLivesRemaining(Math.max(0, player.getLivesRemaining() + lifeDelta));
        if (correct) {
            player.setCurrentStreak(player.getCurrentStreak() + 1);
            player.setBestStreakInMatch(Math.max(player.getBestStreakInMatch(), player.getCurrentStreak()));
        } else {
            player.setCurrentStreak(0);
        }

        MatchRound round = createRound(session.match(), question);
        createAnswer(round, userId, request.value().toPlainString(), deviationPercent.doubleValue(), lifeDelta, correct);

        boolean gameOver = player.getLivesRemaining() == 0;
        QuestionResponse nextQuestion = null;
        if (gameOver) {
            finishMatch(session.match(), player, MatchResult.LOSS);
            statsService.recordFinishedGame(userId, GameMode.PRECISION, player, averageDeviation(session.match().getId(), userId));
        } else {
            nextQuestion = questions.toResponse(questions.findRandomActiveQuestion(QuestionType.NUMERIC, null));
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
                gameOver);
    }

    private StartGameResponse start(UUID userId, GameMode mode, int lives, QuestionType questionType) {
        Match match = matches.save(Match.builder()
                .mode(mode)
                .status(MatchStatus.IN_PROGRESS)
                .ownerUserId(userId)
                .build());
        MatchPlayer player = MatchPlayer.builder()
                .id(new MatchPlayerId(match.getId(), userId))
                .livesRemaining(lives)
                .score(0)
                .currentStreak(0)
                .bestStreakInMatch(0)
                .roundsPlayed(0)
                .build();
        matchPlayers.save(player);

        Question firstQuestion = questions.findRandomActiveQuestion(questionType, null);
        return new StartGameResponse(match.getId(), questions.toResponse(firstQuestion));
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

    private MatchRound createRound(Match match, Question question) {
        long existingRounds = matchRounds.countByMatchId(match.getId());
        return matchRounds.save(MatchRound.builder()
                .matchId(match.getId())
                .questionId(question.getId())
                .roundNumber((int) existingRounds + 1)
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

    private Double averageDeviation(UUID matchId, UUID userId) {
        Set<UUID> roundIds = matchRounds.findAll().stream()
                .filter(round -> matchId.equals(round.getMatchId()))
                .map(MatchRound::getId)
                .collect(Collectors.toSet());
        return matchAnswers.findAll().stream()
                .filter(answer -> roundIds.contains(answer.getRoundId()))
                .filter(answer -> userId.equals(answer.getUserId()))
                .map(MatchAnswer::getDeviation)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private record Session(Match match, MatchPlayer player) {
    }
}
