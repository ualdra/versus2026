import { Component, ElementRef, HostListener, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { AchievementService } from '../../../../core/services/achievement.service';
import { StatsService } from '../../../../core/services/stats.service';
import { UserService } from '../../../../core/services/user.service';
import { SocialService } from '../../../../core/services/social.service';
import { Achievement } from '../../../../core/models/achievement.models';
import { PlayerStats } from '../../../../core/models/game.models';
import type { NotificationItem } from '../../../../core/models/notification.models';
import { NotificationCenterService } from '../../../../core/services/notification-center.service';
import { AvatarComponent } from '../../ui/avatar/avatar.component';


export type NavKey = 'home' | 'play' | 'ranking' | 'friends' | 'profile' | 'settings' | 'admin' | 'users' | 'spiders' | 'reports';
export type TopbarUser = { name: string; xp: number; avatarUrl?: string | null };

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, AvatarComponent],
  templateUrl: './topbar.html',
})
export class TopbarComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);
  private readonly statsApi = inject(StatsService);
  private readonly achievementsApi = inject(AchievementService);
  private readonly notifications = inject(NotificationCenterService);
  private readonly social = inject(SocialService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly menuOpen = signal(false);
  readonly loggingOut = signal(false);
  readonly inviteActioning = signal<Set<string>>(new Set());

  active = input<NavKey>('home');
  role = input<'player' | 'admin'>('player');
  user = input<TopbarUser | null>(null);

  private readonly stats = signal<PlayerStats[]>([]);
  private readonly achievements = signal<Achievement[]>([]);
  readonly notificationItems = this.notifications.items;
  readonly unreadCount = this.notifications.unreadCount;
  readonly notificationsOpen = signal(false);
  readonly unreadLabel = computed(() => {
    const count = this.unreadCount();
    return count > 9 ? '9+' : String(count);
  });

  items = computed<[NavKey, string][]>(() =>
    this.role() === 'admin'
      ? [['admin', 'Resumen'], ['users', 'Usuarios'], ['spiders', 'Spiders'], ['reports', 'Moderación']]
      : [['home', 'Inicio'], ['play', 'Jugar'], ['ranking', 'Ranking'], ['friends', 'Amigos'], ['profile', 'Perfil']]
  );

  private routes: Record<NavKey, string | null> = {
    home: '/dashboard',
    play: '/play/select',
    ranking: '/rankings',
    friends: '/friends',
    profile: '/profile',
    settings: '/settings',
    admin: '/admin/dashboard',
    users: '/admin/users',
    spiders: '/admin/spiders',
    reports: '/admin/reports',
  };

  routeFor(k: NavKey): string | null {
    return this.routes[k];
  }

  readonly displayUser = computed<TopbarUser>(() => {
    const override = this.user();
    if (override) return override;
    const cached = this.auth.user();
    return {
      name: cached?.username ?? 'Jugador',
      avatarUrl: cached?.avatarUrl,
      xp: this.calculateXp(this.stats()),
    };
  });

  latestAchievement = computed(() => {
    const unlocked = this.achievements().filter((achievement) => achievement.unlocked);
    return unlocked.sort((a, b) => {
      const at = a.unlockedAt ? new Date(a.unlockedAt).getTime() : 0;
      const bt = b.unlockedAt ? new Date(b.unlockedAt).getTime() : 0;
      return bt - at;
    })[0] ?? null;
  });

  ngOnInit(): void {
    if (!this.auth.isAuthenticated()) return;
    this.notifications.start();
    this.users.me().subscribe({
      next: (u) => this.auth.updateCachedUser({ username: u.username, avatarUrl: u.avatarUrl, role: u.role }),
      error: () => {},
    });
    this.statsApi.mine().subscribe({
      next: (list) => this.stats.set(list?.byMode ?? []),
      error: () => this.stats.set([]),
    });
    this.achievementsApi.list().subscribe({
      next: (list) => this.achievements.set(list ?? []),
      error: () => this.achievements.set([]),
    });
  }

  achievementLabel(achievement: Achievement): string {
    const labels: Record<string, string> = {
      first: '1',
      win: 'W',
      streak5: '5',
      streak10: '10',
      streak20: '20',
      precision: 'P',
      sniper: 'P',
      target: 'P',
      survival: 'S',
      shield: 'S',
      perfect: 'S',
      duel: 'D',
      arena: 'D',
      sabotage: 'SB',
      friends: 'F',
      invite: 'I',
      collector: 'C',
    };
    return labels[achievement.iconKey] ?? 'OK';
  }

  toggleNotifications(event: MouseEvent): void {
    event.stopPropagation();
    this.notificationsOpen.update((open) => !open);
  }

  closeNotifications(): void {
    this.notificationsOpen.set(false);
  }

  markAllNotificationsRead(event: MouseEvent): void {
    event.stopPropagation();
    this.notifications.markAllRead();
  }

  clearNotifications(event: MouseEvent): void {
    event.stopPropagation();
    this.notifications.clear();
  }

  selectNotification(notification: NotificationItem): void {
    this.notifications.markRead(notification.id);
    this.closeNotifications();
  }

  isInviteActioning(notificationId: string): boolean {
    return this.inviteActioning().has(notificationId);
  }

  acceptInvite(notification: NotificationItem, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (!notification.inviteId || this.isInviteActioning(notification.id)) return;

    this.inviteActioning.update((set) => new Set([...set, notification.id]));
    this.social.acceptMatchInvite(notification.inviteId).subscribe({
      next: (lobby) => {
        this.notifications.remove(notification.id);
        this.closeNotifications();
        this.router.navigate(['/play/lobby', lobby.matchId]);
      },
      error: () => {
        this.inviteActioning.update((set) => {
          const next = new Set(set);
          next.delete(notification.id);
          return next;
        });
      },
    });
  }

  declineInvite(notification: NotificationItem, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (!notification.inviteId || this.isInviteActioning(notification.id)) return;

    this.inviteActioning.update((set) => new Set([...set, notification.id]));
    this.social.declineMatchInvite(notification.inviteId).subscribe({
      next: () => this.notifications.remove(notification.id),
      error: () => {
        this.inviteActioning.update((set) => {
          const next = new Set(set);
          next.delete(notification.id);
          return next;
        });
      },
    });
  }

  notificationIcon(notification: NotificationItem): string {
    if (notification.type === 'ACHIEVEMENT_UNLOCKED') return 'OK';
    if (notification.type === 'MATCH_FOUND') return 'VS';
    if (notification.type === 'FRIEND_REQUEST') return 'F';
    if (notification.type === 'MATCH_INVITE') return 'I';
    if (notification.tone === 'danger') return '!';
    return 'i';
  }

  notificationTime(notification: NotificationItem): string {
    const time = new Date(notification.createdAt).getTime();
    if (!Number.isFinite(time)) return '';

    const diff = Math.max(0, Date.now() - time);
    const minute = 60 * 1000;
    const hour = 60 * minute;
    if (diff < minute) return 'ahora';
    if (diff < hour) return `${Math.floor(diff / minute)} min`;
    if (diff < 24 * hour) return `${Math.floor(diff / hour)} h`;
    return new Date(time).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' });
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  logout(): void {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    this.closeMenu();
    this.auth.logout().subscribe({
      next: () => {
        this.loggingOut.set(false);
        this.router.navigate(['/login']);
      },
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.closeNotifications();
      this.closeMenu();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeNotifications();
    this.closeMenu();
  }

  private calculateXp(stats: PlayerStats[]): number {
    const games = stats.reduce((total, s) => total + s.gamesPlayed, 0);
    const wins = stats.reduce((total, s) => total + s.gamesWon, 0);
    const bestStreak = stats.reduce((best, s) => Math.max(best, s.bestStreak), 0);
    return games * 50 + wins * 150 + bestStreak * 25;
  }
}
