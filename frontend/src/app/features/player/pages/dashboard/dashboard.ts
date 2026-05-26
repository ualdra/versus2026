import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AuthService } from '../../../../core/services/auth.service';
import { StatsService } from '../../../../core/services/stats.service';
import { PlayerStats } from '../../../../core/models/game.models';
import { EloWidgetComponent } from '../../components/elo-widget/elo-widget';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, TopbarComponent, UpperCasePipe, EloWidgetComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly statsApi = inject(StatsService);
  private readonly router = inject(Router);

  readonly username = computed(() => this.auth.user()?.username ?? '');

  private readonly statsList = signal<PlayerStats[]>([]);
  readonly loading = signal(true);

  readonly stats = computed(() => {
    const list = this.statsList();
    const totalGames = list.reduce((s, x) => s + x.gamesPlayed, 0);
    const totalWins  = list.reduce((s, x) => s + x.gamesWon, 0);
    const winRate = totalGames === 0 ? 0 : Math.round((totalWins / totalGames) * 100);
    const bestStreak = list.reduce((m, x) => Math.max(m, x.bestStreak), 0);
    return [
      { num: String(totalGames), label: 'Partidas' },
      { num: `${winRate}%`,      label: 'Victorias',     accent: 'var(--vs-accent-green)' },
      { num: String(bestStreak), label: 'Mejor racha',   accent: 'var(--vs-accent-gold)' },
      { num: '—',                label: 'Ranking global', accent: 'var(--vs-accent-blue)' },
    ];
  });

  modes = [
    { name: 'Supervivencia', color: 'var(--vs-accent-red)',    desc: '3 vidas. Cero piedad.', badge: 'Más jugado' },
    { name: 'Precisión',     color: 'var(--vs-accent-blue)',   desc: 'Adivina el número' },
    { name: 'Duelo binario', color: 'var(--vs-accent-gold)',   desc: '2 jugadores' },
    { name: 'Sabotaje',      color: 'var(--vs-accent-purple)', desc: 'Quítale vida al rival' },
  ];

  // Activity + rankings: real endpoints land in Sprint 3 (history) and ranking phases.
  activity: { mode: string; modeClass: string; result: string; resultColor: string; streak: number; pts: string; when: string }[] = [];
  rankings: { pos: number; name: string; score: number; you: boolean }[] = [];

  ngOnInit(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login']);
      return;
    }
    this.statsApi.mine().subscribe({
      next: (list) => {
        this.statsList.set(list?.byMode ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  formatScore(n: number): string {
    return n.toLocaleString('es-ES');
  }
}
