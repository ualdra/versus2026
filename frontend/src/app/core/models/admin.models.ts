import { Role } from './auth.models';

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  avatarUrl: string | null;
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

export interface AdminSpider {
  id: string;
  name: string;
  targetUrl: string;
  status: 'IDLE' | 'RUNNING' | 'FAILED';
  lastRunAt: string | null;
  lastRun: {
    id: string;
    startedAt: string;
    finishedAt: string | null;
    questionsInserted: number;
    errors: number;
  } | null;
}

export interface ReportOption {
  text: string;
  isCorrect: boolean;
}

export interface AdminReport {
  id: string;
  questionId: string;
  questionText: string | null;
  reason: 'WRONG_ANSWER' | 'OUTDATED' | 'OFFENSIVE' | 'OTHER';
  status: 'PENDING' | 'DISMISSED' | 'RESOLVED';
  comment: string | null;
  createdAt: string;
  resolvedBy: string | null;
  resolvedAt: string | null;
  options: ReportOption[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
