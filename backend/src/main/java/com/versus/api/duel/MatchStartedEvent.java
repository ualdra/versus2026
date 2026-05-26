package com.versus.api.duel;

import com.versus.api.match.state.LiveMatchState;

public record MatchStartedEvent(LiveMatchState state) {
}
