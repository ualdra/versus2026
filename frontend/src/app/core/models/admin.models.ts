import { Role } from './auth.models';

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  role: Role;
  isActive: boolean;
  createdAt: string;
}

export interface AdminUserPage {
  items: AdminUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminStats {
  totalUsers: number;
  activeUsers: number;
  matchesToday: number;
  totalQuestions: number;
  activeSpiders: number;
  pendingReports: number;
}

export interface AdminLog {
  ts: string;
  level: 'INFO' | 'WARN' | 'ERR';
  message: string;
}
