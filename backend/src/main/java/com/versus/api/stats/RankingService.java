package com.versus.api.stats;

import com.versus.api.common.exception.ApiException;
import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;
import com.versus.api.match.MatchService;
import com.versus.api.match.dto.MatchHistoryItemResponse;
import com.versus.api.stats.domain.Ranking;
import com.versus.api.stats.dto.EloCalculationResponse;
import com.versus.api.stats.dto.EloChangeResponse;
import com.versus.api.stats.dto.RankingEntryResponse;
import com.versus.api.stats.dto.RankingSummaryResponse;
import com.versus.api.stats.dto.UserRankingResponse;
import com.versus.api.stats.repo.RankingRepository;
import com.versus.api.users.domain.User;
import com.versus.api.users.dto.UserPublicResponse;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int INITIAL_RATING = 1000;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_HISTORY_SIZE = 10;

    private final RankingRepository rankings;
    private final UserRepository users;
    private final MatchService matchService;
    private final EloService eloService;

    @Transactional
    public Map<UUID, EloChangeResponse> recordPvpResult(GameMode mode, Map<UUID, MatchResult> results) {
        if (mode == null || !mode.isMultiplayer() || results == null || results.size() != 2) {
            return Map.of();
        }

        List<UUID> winners = usersByResult(results, MatchResult.WIN);
        List<UUID> losers = usersByResult(results, MatchResult.LOSS);

        if (winners.size() != 1 || losers.size() != 1) {
            return recordNoRatingChange(mode, results.keySet().stream().toList());
        }

        Ranking winner = findOrCreateForUpdate(winners.get(0), mode);
        Ranking loser = findOrCreateForUpdate(losers.get(0), mode);

        int previousWinnerRating = winner.getRating();
        int previousLoserRating = loser.getRating();
        EloCalculationResponse elo = eloService.calculate(previousWinnerRating, previousLoserRating);

        winner.setRating(previousWinnerRating + elo.winnerDelta());
        winner.setWins(winner.getWins() + 1);
        winner.setWinStreak(winner.getWinStreak() + 1);
        winner.setLastDelta(elo.winnerDelta());

        loser.setRating(Math.max(0, previousLoserRating + elo.loserDelta()));
        loser.setLosses(loser.getLosses() + 1);
        loser.setWinStreak(0);
        loser.setLastDelta(elo.loserDelta());

        rankings.save(winner);
        rankings.save(loser);

        Map<UUID, EloChangeResponse> changes = new LinkedHashMap<>();
        changes.put(winner.getUserId(), new EloChangeResponse(
                previousWinnerRating, winner.getRating(), elo.winnerDelta()));
        changes.put(loser.getUserId(), new EloChangeResponse(
                previousLoserRating, loser.getRating(), elo.loserDelta()));
        return changes;
    }

    @Transactional(readOnly = true)
    public Page<RankingEntryResponse> getLeaderboard(GameMode mode, int page, int size, UUID currentUserId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(
                Sort.Order.desc("rating"),
                Sort.Order.desc("wins"),
                Sort.Order.asc("updatedAt")));
        Page<Ranking> rankingPage = rankings.findByMode(mode, pageable);

        Map<UUID, User> userMap = users.findAllById(
                        rankingPage.getContent().stream().map(Ranking::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        long offset = (long) safePage * safeSize;
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger(0);
        List<RankingEntryResponse> content = rankingPage.getContent().stream()
                .map(ranking -> toEntry(
                        ranking,
                        userMap.get(ranking.getUserId()),
                        offset + index.incrementAndGet(),
                        Objects.equals(currentUserId, ranking.getUserId())))
                .toList();

        return new PageImpl<>(content, pageable, rankingPage.getTotalElements());
    }

    @Transactional
    public List<RankingSummaryResponse> getMine(UUID userId) {
        users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return Arrays.stream(GameMode.values())
                .map(mode -> toSummary(findOrCreate(userId, mode)))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserRankingResponse getUserRanking(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        Map<GameMode, Ranking> byMode = rankings.findByUserId(userId).stream()
                .collect(Collectors.toMap(Ranking::getMode, Function.identity()));
        List<RankingSummaryResponse> summaries = Arrays.stream(GameMode.values())
                .map(mode -> {
                    Ranking ranking = byMode.get(mode);
                    return ranking == null ? initialSummary(mode) : toSummary(ranking);
                })
                .toList();
        List<MatchHistoryItemResponse> history = matchService
                .getHistory(userId, 0, DEFAULT_HISTORY_SIZE, null)
                .getContent();
        return new UserRankingResponse(toPublicUser(user), summaries, history);
    }

    private Map<UUID, EloChangeResponse> recordNoRatingChange(GameMode mode, List<UUID> userIds) {
        Map<UUID, EloChangeResponse> changes = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            Ranking ranking = findOrCreateForUpdate(userId, mode);
            int rating = ranking.getRating();
            ranking.setLastDelta(0);
            rankings.save(ranking);
            changes.put(userId, new EloChangeResponse(rating, rating, 0));
        }
        return changes;
    }

    private List<UUID> usersByResult(Map<UUID, MatchResult> results, MatchResult result) {
        return results.entrySet().stream()
                .filter(entry -> entry.getValue() == result)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Ranking findOrCreateForUpdate(UUID userId, GameMode mode) {
        return rankings.findByUserIdAndModeForUpdate(userId, mode)
                .orElseGet(() -> rankings.save(initialRanking(userId, mode)));
    }

    private Ranking findOrCreate(UUID userId, GameMode mode) {
        return rankings.findByUserIdAndMode(userId, mode)
                .orElseGet(() -> rankings.save(initialRanking(userId, mode)));
    }

    private Ranking initialRanking(UUID userId, GameMode mode) {
        return Ranking.builder()
                .userId(userId)
                .mode(mode)
                .rating(INITIAL_RATING)
                .wins(0)
                .losses(0)
                .winStreak(0)
                .lastDelta(0)
                .build();
    }

    private RankingSummaryResponse initialSummary(GameMode mode) {
        return new RankingSummaryResponse(mode, 0, INITIAL_RATING, 0, 0, 0, 0);
    }

    private RankingSummaryResponse toSummary(Ranking ranking) {
        return new RankingSummaryResponse(
                ranking.getMode(),
                rankOf(ranking),
                ranking.getRating(),
                ranking.getWins(),
                ranking.getLosses(),
                ranking.getWinStreak(),
                ranking.getLastDelta());
    }

    private RankingEntryResponse toEntry(Ranking ranking, User user, long rank, boolean currentUser) {
        return new RankingEntryResponse(
                ranking.getUserId(),
                user == null ? "Unknown" : user.getUsername(),
                user == null ? null : user.getAvatarUrl(),
                ranking.getMode(),
                rank,
                ranking.getRating(),
                ranking.getWins(),
                ranking.getLosses(),
                ranking.getWinStreak(),
                ranking.getLastDelta(),
                ranking.getUpdatedAt(),
                currentUser);
    }

    private long rankOf(Ranking ranking) {
        Instant updatedAt = ranking.getUpdatedAt() == null ? Instant.now() : ranking.getUpdatedAt();
        return rankings.countBetterRank(
                ranking.getMode(),
                ranking.getRating(),
                ranking.getWins(),
                updatedAt) + 1;
    }

    private UserPublicResponse toPublicUser(User user) {
        return new UserPublicResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}
