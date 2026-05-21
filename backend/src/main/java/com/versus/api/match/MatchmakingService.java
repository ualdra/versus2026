package com.versus.api.match;

import com.versus.api.common.exception.ApiException;
import com.versus.api.match.domain.MatchmakingQueue;
import com.versus.api.match.dto.MatchFoundEvent;
import com.versus.api.match.dto.PlayerInLobbyDto;
import com.versus.api.match.repo.MatchmakingQueueRepository;
import com.versus.api.match.state.LiveMatchState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final MatchmakingQueueRepository queueRepository;
    private final MatchService matchService;

    @Transactional
    public void joinQueue(UUID userId, GameMode mode) {
        if (!mode.isMultiplayer()) {
            throw ApiException.validation("Mode " + mode + " is single-player");
        }
        queueRepository.findByUserId(userId).ifPresent(existing -> {
            if (existing.getMode() != mode) {
                queueRepository.delete(existing);
            }
        });
        if (queueRepository.findByUserId(userId).isEmpty()) {
            queueRepository.save(MatchmakingQueue.builder()
                    .userId(userId)
                    .mode(mode)
                    .build());
            log.info("User {} entered queue for {}", userId, mode);
        }
    }

    @Transactional
    public void leaveQueue(UUID userId) {
        queueRepository.deleteByUserId(userId);
        log.info("User {} left queue", userId);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndMatch() {
        for (GameMode mode : GameMode.values()) {
            if (!mode.isMultiplayer()) continue;
            List<MatchmakingQueue> waiting = queueRepository.findByModeOrderByEnteredAtAsc(mode);
            int n = mode.requiredPlayers();
            while (waiting.size() >= n) {
                List<MatchmakingQueue> picked = new ArrayList<>(waiting.subList(0, n));
                pairUp(mode, picked);
                queueRepository.deleteAll(picked);
                waiting = waiting.subList(n, waiting.size());
            }
        }
    }

    private void pairUp(GameMode mode, List<MatchmakingQueue> entries) {
        UUID owner = entries.get(0).getUserId();
        LiveMatchState state = matchService.createMatch(mode, owner);
        for (MatchmakingQueue entry : entries) {
            matchService.addPlayer(state.getMatchId(), entry.getUserId());
        }
        for (MatchmakingQueue entry : entries) {
            UUID self = entry.getUserId();
            List<PlayerInLobbyDto> opponents = state.getPlayers().values().stream()
                    .filter(p -> !p.getUserId().equals(self))
                    .map(matchService::toDto)
                    .toList();
            matchService.notifyMatchFound(self,
                    new MatchFoundEvent(state.getMatchId(), mode, opponents));
        }
        log.info("Matched {} players in mode {} → match {}", entries.size(), mode, state.getMatchId());
    }
}
