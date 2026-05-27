import { Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { Role } from '../../../../core/models/auth.models';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';

export type AdminNavKey = 'dash' | 'spiders' | 'reports' | 'users';

interface NavItem {
  key: AdminNavKey;
  label: string;
  route: string;
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

  sections: { label: string; items: NavItem[] }[] = [
    {
      label: 'SUPERVISIÓN',
      items: [
        { key: 'dash', label: 'Resumen', route: '/admin/dashboard' },
        { key: 'spiders', label: 'Spiders', route: '/admin/spiders' },
        { key: 'reports', label: 'Moderación', route: '/admin/reports' },
      ],
    },
    {
      label: 'GESTIÓN',
      items: [{ key: 'users', label: 'Usuarios', route: '/admin/users' }],
    },
  ];

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
