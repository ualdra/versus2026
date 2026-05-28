import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Role } from '../models/auth.models';
import { ProposalStatus, QuestionProposal } from '../models/question-proposal.models';
import {
  AdminLog,
  AdminReport,
  AdminSpider,
  AdminStats,
  AdminUser,
  AdminUserPage,
  ModeDistribution,
  PageResponse,
} from '../models/admin.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  // ── Stats ──────────────────────────────────────────────────────────────────

  stats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.base}/admin/stats`);
  }

  logs(limit = 20): Observable<AdminLog[]> {
    return this.http.get<AdminLog[]>(`${this.base}/admin/logs`, {
      params: new HttpParams().set('limit', limit),
    });
  }

  modeDistribution(): Observable<ModeDistribution[]> {
    return this.http.get<ModeDistribution[]>(`${this.base}/admin/stats/modes`);
  }

  // ── Users ──────────────────────────────────────────────────────────────────

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

  // ── Spiders ────────────────────────────────────────────────────────────────

  getSpiders(): Observable<AdminSpider[]> {
    return this.http.get<AdminSpider[]>(`${this.base}/admin/spiders`);
  }

  triggerSpider(name: string): Observable<unknown> {
    return this.http.post(`${this.base}/admin/spiders/${name}/run`, {});
  }

  // ── Reports ────────────────────────────────────────────────────────────────

  getReports(status?: string, page = 0): Observable<PageResponse<AdminReport>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (status) params = params.set('status', status);
    return this.http.get<PageResponse<AdminReport>>(
      `${this.base}/moderation/reports`,
      { params },
    );
  }

  resolveReport(
    id: string,
    action: 'DISMISS' | 'DELETE_QUESTION' | 'EDIT_QUESTION',
  ): Observable<AdminReport> {
    return this.http.put<AdminReport>(
      `${this.base}/moderation/reports/${id}/resolve`,
      { action },
    );
  }

  // ── Question proposals ──────────────────────────────────────────────────────

  getProposals(status?: ProposalStatus, page = 0): Observable<PageResponse<QuestionProposal>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (status) params = params.set('status', status);
    return this.http.get<PageResponse<QuestionProposal>>(
      `${this.base}/moderation/proposals`,
      { params },
    );
  }

  approveProposal(id: string): Observable<QuestionProposal> {
    return this.http.put<QuestionProposal>(
      `${this.base}/moderation/proposals/${id}/approve`,
      {},
    );
  }

  rejectProposal(id: string, rejectReason: string): Observable<QuestionProposal> {
    return this.http.put<QuestionProposal>(
      `${this.base}/moderation/proposals/${id}/reject`,
      { rejectReason },
    );
  }
}
