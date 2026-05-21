export type Role = 'PLAYER' | 'MODERATOR' | 'ADMIN';

export interface AuthUser {
  id: string;
  username: string;
  role: Role;
  avatarUrl: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface UserMe {
  id: string;
  username: string;
  email: string;
  avatarUrl: string | null;
  role: Role;
  createdAt: string;
}

export interface ApiError {
  error: string;
  message: string;
  status: number;
}

export interface MessageResponse {
  message: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirmRequest {
  token: string;
  newPassword: string;
}
