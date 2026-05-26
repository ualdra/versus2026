import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GameMode } from '../models/game.models';
import { RankingPage, RankingSummary, UserRanking } from '../models/ranking.models';

@Injectable({ providedIn: 'root' })
export class RankingService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  leaderboard(mode: GameMode, page = 0, size = 20): Observable<RankingPage> {
    const params = new HttpParams()
      .set('mode', mode)
      .set('page', page)
      .set('size', size);
    return this.http.get<RankingPage>(`${this.base}/rankings`, { params });
  }

  mine(): Observable<RankingSummary[]> {
    return this.http.get<RankingSummary[]>(`${this.base}/rankings/me`);
  }

  userRanking(userId: string): Observable<UserRanking> {
    return this.http.get<UserRanking>(`${this.base}/users/${userId}/ranking`);
  }
}
