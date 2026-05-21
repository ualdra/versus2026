import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  GameMode,
  LobbyState,
  MatchCreated,
  MatchFoundPayload,
} from '../models/match.models';
import { MatchEvent } from '../models/ws.models';
import { WebSocketService } from './websocket.service';

@Injectable({ providedIn: 'root' })
export class MatchService {
  private readonly http = inject(HttpClient);
  private readonly ws = inject(WebSocketService);
  private readonly base = environment.apiBaseUrl;

  // ─── REST ────────────────────────────────────────────────────────────────
  createMatch(mode: GameMode): Observable<MatchCreated> {
    return this.http.post<MatchCreated>(`${this.base}/matches`, { mode });
  }

  joinMatch(matchId: string): Observable<LobbyState> {
    return this.http.post<LobbyState>(`${this.base}/matches/${matchId}/join`, {});
  }

  abandonMatch(matchId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/matches/${matchId}/abandon`);
  }

  getLobby(matchId: string): Observable<LobbyState> {
    return this.http.get<LobbyState>(`${this.base}/matches/${matchId}/lobby`);
  }

  joinQueue(mode: GameMode): Observable<void> {
    return this.http.post<void>(`${this.base}/matchmaking/queue`, { mode });
  }

  leaveQueue(): Observable<void> {
    return this.http.delete<void>(`${this.base}/matchmaking/queue`);
  }

  // ─── WebSocket ───────────────────────────────────────────────────────────
  matchFound$(): Observable<MatchEvent<MatchFoundPayload>> {
    return this.ws.subscribe<MatchEvent<MatchFoundPayload>>('/user/queue/match');
  }

  lobbyEvents$(matchId: string): Observable<MatchEvent<unknown>> {
    return this.ws.subscribe<MatchEvent<unknown>>(`/topic/match/${matchId}`);
  }

  sendReady(matchId: string): void {
    this.ws.publish('/app/match/ready', { matchId });
  }

  sendUnready(matchId: string): void {
    this.ws.publish('/app/match/unready', { matchId });
  }

  sendAbandon(matchId: string): void {
    this.ws.publish('/app/match/abandon', { matchId });
  }
}
