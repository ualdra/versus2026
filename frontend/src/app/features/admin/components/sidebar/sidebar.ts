import { Component, inject, input } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';

export type AdminNavKey =
  | 'dash'
  | 'spiders'
  | 'reports'
  | 'users'
  | 'quest'
  | 'rank'
  | 'cfg'
  | 'logs';

const ROUTES: Partial<Record<AdminNavKey, string>> = {
  dash: '/admin/dashboard',
  spiders: '/admin/spiders',
  reports: '/admin/reports',
  users: '/admin/users',
};

@Component({
  selector: 'app-admin-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AvatarComponent],
  templateUrl: './sidebar.html',
})
export class AdminSidebarComponent {
  active = input<AdminNavKey>('dash');

  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  sections = [
    {
      label: 'SUPERVISIÓN',
      items: [
        { key: 'dash', label: 'Resumen' },
        { key: 'spiders', label: 'Spiders' },
        { key: 'reports', label: 'Moderación' },
      ],
    },
    {
      label: 'GESTIÓN',
      items: [
        { key: 'users', label: 'Usuarios' },
        { key: 'quest', label: 'Preguntas' },
        { key: 'rank', label: 'Rankings' },
      ],
    },
    {
      label: 'SISTEMA',
      items: [
        { key: 'cfg', label: 'Configuración' },
        { key: 'logs', label: 'Logs' },
      ],
    },
  ];

  route(key: string): string | null {
    return ROUTES[key as AdminNavKey] ?? null;
  }

  navigate(key: string): void {
    const r = this.route(key);
    if (r) this.router.navigate([r]);
  }

}
