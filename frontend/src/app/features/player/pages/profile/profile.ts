import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AuthService } from '../../../../core/services/auth.service';
import { UserService } from '../../../../core/services/user.service';
import { StatsService } from '../../../../core/services/stats.service';
import { AchievementService } from '../../../../core/services/achievement.service';
import { Achievement } from '../../../../core/models/achievement.models';
import { UserMe } from '../../../../core/models/auth.models';
import { GameMode, PlayerStats } from '../../../../core/models/game.models';

const MODE_LABEL: Record<GameMode, string> = {
  SURVIVAL: 'Supervivencia',
  PRECISION: 'Precisión',
  BINARY_DUEL: 'Duelo binario',
  PRECISION_DUEL: 'Duelo de precisión',
  SABOTAGE: 'Sabotaje',
};

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [RouterLink, TopbarComponent],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);
  private readonly statsApi = inject(StatsService);
  private readonly achievementsApi = inject(AchievementService);

  readonly me = signal<UserMe | null>(null);
  readonly statsList = signal<PlayerStats[]>([]);
  readonly achievements = signal<Achievement[]>([]);

  readonly initials = computed(() => {
    const u = this.me()?.username ?? this.auth.user()?.username ?? '';
    return u.slice(0, 2).toUpperCase() || '??';
  });

  readonly username = computed(() => this.me()?.username ?? this.auth.user()?.username ?? '—');

  readonly joined = computed(() => {
    const iso = this.me()?.createdAt;
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
  });

  readonly totalGames = computed(() =>
    this.statsList().reduce((s, x) => s + x.gamesPlayed, 0)
  );

  readonly bestStreakOverall = computed(() =>
    this.statsList().reduce((m, x) => Math.max(m, x.bestStreak), 0)
  );

  readonly unlockedAchievements = computed(() =>
    this.achievements().filter((achievement) => achievement.unlocked)
  );

  readonly unlockedCount = computed(() => this.unlockedAchievements().length);
  readonly totalAchievements = computed(() => this.achievements().length);

  readonly modeStats = computed(() =>
    this.statsList().map((s) => ({
      mode: MODE_LABEL[s.mode] ?? s.mode,
      games: s.gamesPlayed,
      wins: s.gamesWon,
      acc: s.gamesPlayed === 0 ? '—' : `${s.winRate}%`,
      best: s.bestStreak,
    }))
  );

  categories: { cat: string; pct: number }[] = [];
  history: { mode: string; win: boolean; streak: number; pts: string; when: string }[] = [];

  ngOnInit(): void {
    this.users.me().subscribe({ next: (u) => this.me.set(u), error: () => {} });
    this.statsApi.mine().subscribe({ next: (l) => this.statsList.set(l ?? []), error: () => {} });
    this.achievementsApi.list().subscribe({
      next: (list) => this.achievements.set(list ?? []),
      error: () => this.achievements.set([]),
    });
  }

  achievementIcon(iconKey: string): string {
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
      lock: '?',
    };
    return labels[iconKey] ?? '?';
  }

  achievementDate(achievement: Achievement): string {
    if (!achievement.unlockedAt) return 'Bloqueado';
    return new Date(achievement.unlockedAt).toLocaleDateString('es-ES', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }
}
