import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'landing', pathMatch: 'full' },

  // ─── Pública ──────────────────────────────────────────────────────────────
  {
    path: 'landing',
    loadComponent: () => import('./features/landing/pages/landing/landing').then(m => m.Landing),
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/pages/login/login').then(m => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/pages/register/register').then(m => m.Register),
  },
  {
    path: 'check-email',
    loadComponent: () => import('./features/auth/pages/check-email/check-email').then(m => m.CheckEmail),
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./features/auth/pages/verify-email/verify-email').then(m => m.VerifyEmail),
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./features/auth/pages/forgot-password/forgot-password').then(m => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./features/auth/pages/reset-password/reset-password').then(m => m.ResetPassword),
  },

  // ─── Jugador ──────────────────────────────────────────────────────────────
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/player/pages/dashboard/dashboard').then(m => m.Dashboard),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/player/pages/profile/profile').then(m => m.Profile),
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/player/pages/settings/settings').then(m => m.Settings),
  },
  {
    path: 'play',
    canActivate: [authGuard],
    children: [
      {
        path: 'select',
        loadComponent: () => import('./features/player/pages/mode-select/mode-select').then(m => m.ModeSelect),
      },
      {
        path: 'queue/:mode',
        loadComponent: () => import('./features/player/pages/queue/queue').then(m => m.Queue),
      },
      {
        path: 'lobby/:matchId',
        loadComponent: () => import('./features/player/pages/lobby/lobby').then(m => m.Lobby),
      },
      {
        path: 'survival',
        loadComponent: () => import('./features/survival/pages/survival/survival').then(m => m.Survival),
      },
      {
        path: 'precision',
        loadComponent: () => import('./features/precision/pages/precision/precision').then(m => m.Precision),
      },
      {
        path: 'binary-duel/:matchId',
        loadComponent: () => import('./features/binary-duel/pages/binary-duel/binary-duel').then(m => m.BinaryDuel),
      },
      {
        path: 'precision-duel/:matchId',
        loadComponent: () => import('./features/precision-duel/pages/precision-duel/precision-duel').then(m => m.PrecisionDuel),
      },
      {
        path: 'sabotage/:matchId',
        loadComponent: () => import('./features/sabotage/pages/sabotage/sabotage').then(m => m.Sabotage),
      },
      {
        path: 'result',
        loadComponent: () => import('./features/player/pages/result/result').then(m => m.Result),
      },
      { path: '', redirectTo: 'select', pathMatch: 'full' },
    ],
  },

  // ─── Admin ────────────────────────────────────────────────────────────────
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/admin/pages/dashboard/admin-dashboard').then(m => m.AdminDashboard),
      },
      {
        path: 'spiders',
        loadComponent: () => import('./features/admin/pages/spiders/admin-spiders').then(m => m.AdminSpiders),
      },
      {
        path: 'reports',
        loadComponent: () => import('./features/admin/pages/reports/admin-reports').then(m => m.AdminReports),
      },
      {
        path: 'users',
        loadComponent: () => import('./features/admin/pages/users/admin-users').then(m => m.AdminUsers),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },

  { path: '**', redirectTo: 'landing' },
];
