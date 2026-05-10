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
        path: 'lobby',
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
