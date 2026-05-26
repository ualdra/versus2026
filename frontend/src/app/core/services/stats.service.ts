import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  GameMode,
  MatchDetail,
  MatchHistoryItem,
  PagedResponse,
  PlayerStats,
  PlayerStatsOverview,
} from '../models/game.models';

@Injectable({ providedIn: 'root' })
export class StatsService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  mine(): Observable<PlayerStatsOverview> {
    return this.http.get<PlayerStatsOverview>(`${this.base}/stats/me`);
  }

  mineByMode(mode: GameMode): Observable<PlayerStats> {
    const params = new HttpParams().set('mode', mode);
    return this.http.get<PlayerStats>(`${this.base}/stats/me`, { params });
  }

  history(page: number, size: number, mode?: GameMode): Observable<PagedResponse<MatchHistoryItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (mode) params = params.set('mode', mode);
    return this.http.get<PagedResponse<MatchHistoryItem>>(`${this.base}/users/me/history`, { params });
  }

  matchDetail(id: string): Observable<MatchDetail> {
    return this.http.get<MatchDetail>(`${this.base}/matches/${id}`);
  }
}
