import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Role } from '../models/auth.models';
import { AdminLog, AdminStats, AdminUser, AdminUserPage } from '../models/admin.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listUsers(params: {
    page?: number;
    size?: number;
    search?: string;
    role?: Role;
    active?: boolean;
  } = {}): Observable<AdminUserPage> {
    let p = new HttpParams();
    if (params.page !== undefined) p = p.set('page', params.page);
    if (params.size !== undefined) p = p.set('size', params.size);
    if (params.search) p = p.set('search', params.search);
    if (params.role) p = p.set('role', params.role);
    if (params.active !== undefined) p = p.set('active', params.active);
    return this.http.get<AdminUserPage>(`${this.base}/admin/users`, { params: p });
  }

  updateUserRole(id: string, role: Role): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}/admin/users/${id}/role`, { role });
  }

  updateUserStatus(id: string, active: boolean): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}/admin/users/${id}/status`, { active });
  }

  stats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.base}/admin/stats`);
  }

  logs(limit = 20): Observable<AdminLog[]> {
    return this.http.get<AdminLog[]>(`${this.base}/admin/logs`, {
      params: new HttpParams().set('limit', limit),
    });
  }
}
