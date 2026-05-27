import { Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { Role } from '../../../../core/models/auth.models';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';

export type AdminNavKey =
  | 'dash'
  | 'spiders'
  | 'reports'
  | 'proposals'
  | 'users'
  | 'quest'
  | 'rank'
  | 'cfg'
  | 'logs';

const ROUTES: Partial<Record<AdminNavKey, string>> = {
  dash: '/admin/dashboard',
  spiders: '/admin/spiders',
  reports: '/admin/reports',
  proposals: '/admin/proposals',
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

  readonly auth = inject(AuthService);

  get sections() {
    if (this.auth.user()?.role === 'MODERATOR') {
      return [
        {
          label: 'SUPERVISION',
          items: [
            { key: 'reports', label: 'Reportes' },
            { key: 'proposals', label: 'Propuestas' },
          ],
        },
      ];
    }

    return [
      {
        label: 'SUPERVISION',
        items: [
          { key: 'dash', label: 'Resumen' },
          { key: 'spiders', label: 'Spiders' },
          { key: 'reports', label: 'Reportes' },
          { key: 'proposals', label: 'Propuestas' },
        ],
      },
      {
        label: 'GESTION',
        items: [
          { key: 'users', label: 'Usuarios' },
          { key: 'quest', label: 'Preguntas' },
          { key: 'rank', label: 'Rankings' },
        ],
      },
      {
        label: 'SISTEMA',
        items: [
          { key: 'cfg', label: 'Configuracion' },
          { key: 'logs', label: 'Logs' },
        ],
      },
    ];
  }

  roleLabel(role: Role | undefined): string {
    const labels: Record<Role, string> = {
      ADMIN: 'Administrador',
      MODERATOR: 'Moderador',
      PLAYER: 'Jugador',
    };
    return role ? labels[role] : '—';
  }

  initials(name: string): string {
    return name.slice(0, 2).toUpperCase();
  }
}
