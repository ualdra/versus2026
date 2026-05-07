import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';

export type NavKey = 'home' | 'play' | 'ranking' | 'profile' | 'admin' | 'users' | 'spiders' | 'reports';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './topbar.html',
})
export class TopbarComponent {
  private readonly auth = inject(AuthService);

  active = input<NavKey>('home');
  role = input<'player' | 'admin'>('player');

  readonly authUser = computed(() => this.auth.user());
  readonly username = computed(() => this.authUser()?.username ?? '');
  readonly avatarUrl = computed(() => this.authUser()?.avatarUrl ?? null);
  readonly xp = computed<number | null>(() => null);

  items = computed<[NavKey, string][]>(() =>
    this.role() === 'admin'
      ? [['admin', 'Resumen'], ['users', 'Usuarios'], ['spiders', 'Spiders'], ['reports', 'Moderación']]
      : [['home', 'Inicio'], ['play', 'Jugar'], ['ranking', 'Ranking'], ['profile', 'Perfil']]
  );

  private routes: Record<NavKey, string | null> = {
    home: '/dashboard',
    play: '/play/select',
    ranking: null,
    profile: '/profile',
    admin: '/admin/dashboard',
    users: '/admin/users',
    spiders: '/admin/spiders',
    reports: '/admin/reports',
  };

  routeFor(k: NavKey): string | null {
    return this.routes[k];
  }

  readonly initials = computed(() => this.username().slice(0, 2).toUpperCase() || '??');
}
