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

interface AdminNavItem {
  key: AdminNavKey;
  label: string;
  route: string | null;
}

interface AdminNavSection {
  label: string;
  items: AdminNavItem[];
}

@Component({
  selector: 'app-admin-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AvatarComponent],
  templateUrl: './sidebar.html',
})
export class AdminSidebarComponent {
  active = input<AdminNavKey>('dash');

  readonly auth = inject(AuthService);

  get sections(): AdminNavSection[] {
    if (this.auth.user()?.role === 'MODERATOR') {
      return [
        {
          label: 'SUPERVISION',
          items: [
            this.navItem('reports', 'Reportes'),
            this.navItem('proposals', 'Propuestas'),
          ],
        },
      ];
    }

    return [
      {
        label: 'SUPERVISION',
        items: [
          this.navItem('dash', 'Resumen'),
          this.navItem('spiders', 'Spiders'),
          this.navItem('reports', 'Reportes'),
          this.navItem('proposals', 'Propuestas'),
        ],
      },
      {
        label: 'GESTION',
        items: [
          this.navItem('users', 'Usuarios'),
          this.navItem('quest', 'Preguntas'),
          this.navItem('rank', 'Rankings'),
        ],
      },
      {
        label: 'SISTEMA',
        items: [
          this.navItem('cfg', 'Configuracion'),
          this.navItem('logs', 'Logs'),
        ],
      },
    ];
  }

  private navItem(key: AdminNavKey, label: string): AdminNavItem {
    return { key, label, route: ROUTES[key] ?? null };
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
