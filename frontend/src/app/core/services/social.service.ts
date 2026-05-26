import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GameMode, LobbyState } from '../models/match.models';
import {
  Friend,
  FriendRequest,
  MatchInvite,
  SocialUser,
} from '../models/social.models';

@Injectable({ providedIn: 'root' })
export class SocialService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/social`;

  searchUsers(query: string): Observable<SocialUser[]> {
    const params = new HttpParams().set('query', query);
    return this.http.get<SocialUser[]>(`${this.base}/users/search`, { params });
  }

  friends(): Observable<Friend[]> {
    return this.http.get<Friend[]>(`${this.base}/friends`);
  }

  incomingFriendRequests(): Observable<FriendRequest[]> {
    return this.http.get<FriendRequest[]>(`${this.base}/friend-requests/incoming`);
  }

  outgoingFriendRequests(): Observable<FriendRequest[]> {
    return this.http.get<FriendRequest[]>(`${this.base}/friend-requests/outgoing`);
  }

  sendFriendRequest(toUserId: string): Observable<FriendRequest> {
    return this.http.post<FriendRequest>(`${this.base}/friend-requests`, { toUserId });
  }

  acceptFriendRequest(id: string): Observable<FriendRequest> {
    return this.http.post<FriendRequest>(`${this.base}/friend-requests/${id}/accept`, {});
  }

  declineFriendRequest(id: string): Observable<FriendRequest> {
    return this.http.post<FriendRequest>(`${this.base}/friend-requests/${id}/decline`, {});
  }

  cancelFriendRequest(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/friend-requests/${id}`);
  }

  incomingMatchInvites(): Observable<MatchInvite[]> {
    return this.http.get<MatchInvite[]>(`${this.base}/match-invites/incoming`);
  }

  outgoingMatchInvites(): Observable<MatchInvite[]> {
    return this.http.get<MatchInvite[]>(`${this.base}/match-invites/outgoing`);
  }

  inviteFriend(friendUserId: string, mode: GameMode, matchId?: string): Observable<MatchInvite> {
    const body = matchId ? { friendUserId, mode, matchId } : { friendUserId, mode };
    return this.http.post<MatchInvite>(`${this.base}/match-invites`, body);
  }

  acceptMatchInvite(id: string): Observable<LobbyState> {
    return this.http.post<LobbyState>(`${this.base}/match-invites/${id}/accept`, {});
  }

  declineMatchInvite(id: string): Observable<MatchInvite> {
    return this.http.post<MatchInvite>(`${this.base}/match-invites/${id}/decline`, {});
  }
}
