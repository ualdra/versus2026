import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { RankingService } from '../../../../core/services/ranking.service';
import { GameMode } from '../../../../core/models/game.models';
import { RankingPage } from '../../../../core/models/ranking.models';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';

const MODES: { mode: GameMode; label: string }[] = [
  { mode: 'BINARY_DUEL', label: 'Duelo binario' },
  { mode: 'PRECISION_DUEL', label: 'Duelo de precision' },
  { mode: 'SABOTAGE', label: 'Sabotaje' },
];

@Component({
  selector: 'app-rankings',
  standalone: true,
  imports: [RouterLink, TopbarComponent, AvatarComponent],
  templateUrl: './rankings.html',
  styleUrl: './rankings.scss',
})
export class Rankings implements OnInit {
  private readonly rankingApi = inject(RankingService);
  private readonly auth = inject(AuthService);

  readonly modes = MODES;
  readonly isAuthenticated = this.auth.isAuthenticated;
  readonly activeMode = signal<GameMode>('BINARY_DUEL');
  readonly page = signal(0);
  readonly size = signal(20);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly data = signal<RankingPage | null>(null);
  readonly rows = computed(() => this.data()?.content ?? []);
  readonly totalPages = computed(() => this.data()?.totalPages ?? 0);
  readonly canGoBack = computed(() => this.page() > 0 && !this.loading());
  readonly canGoNext = computed(() => !this.loading() && this.page() + 1 < this.totalPages());

  ngOnInit(): void {
    this.load();
  }

  selectMode(mode: GameMode): void {
    if (this.activeMode() === mode) return;
    this.activeMode.set(mode);
    this.page.set(0);
    this.load();
  }

  nextPage(): void {
    if (!this.canGoNext()) return;
    this.page.update((page) => page + 1);
    this.load();
  }

  previousPage(): void {
    if (!this.canGoBack()) return;
    this.page.update((page) => page - 1);
    this.load();
  }

  signed(delta: number): string {
    if (delta > 0) return `+${delta}`;
    return String(delta);
  }

  deltaClass(delta: number): string {
    if (delta > 0) return 'is-up';
    if (delta < 0) return 'is-down';
    return 'is-flat';
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rankingApi.leaderboard(this.activeMode(), this.page(), this.size()).subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.data.set(null);
        this.error.set('No se pudo cargar el leaderboard.');
        this.loading.set(false);
      },
    });
  }
}
