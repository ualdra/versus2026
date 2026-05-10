import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserMe } from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  me(): Observable<UserMe> {
    return this.http.get<UserMe>(`${this.base}/users/me`);
  }

  updateMe(payload: { username?: string; avatarUrl?: string }): Observable<UserMe> {
    return this.http.put<UserMe>(`${this.base}/users/me`, payload);
  }

  changePassword(payload: { currentPassword: string; newPassword: string }): Observable<void> {
    return this.http.put<void>(`${this.base}/users/me/password`, payload);
  }

  updateAvatarUrl(avatarUrl: string): Observable<UserMe> {
    return this.http.put<UserMe>(`${this.base}/users/me/avatar`, { avatarUrl });
  }

  uploadAvatar(file: Blob): Observable<UserMe> {
    const body = new FormData();
    body.append('file', file, 'avatar.png');
    return this.http.put<UserMe>(`${this.base}/users/me/avatar`, body);
  }

  deleteMe(): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/me`);
  }
}
