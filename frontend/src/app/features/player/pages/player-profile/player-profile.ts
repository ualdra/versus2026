import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';
import { RankingService } from '../../../../core/services/ranking.service';
import { PublicRankingUser, RankingSummary } from '../../../../core/models/ranking.models';
import { GameMode } from '../../../../core/models/game.models';

const MODE_LABEL: Record<GameMode, string> = {
  SURVIVAL: 'Supervivencia',
  PRECISION: 'Precisión',
  BINARY_DUEL: 'Duelo binario',
  PRECISION_DUEL: 'Duelo de precisión',
  SABOTAGE: 'Sabotaje',
};

const MULTIPLAYER_MODES: GameMode[] = ['BINARY_DUEL', 'PRECISION_DUEL', 'SABOTAGE'];

@Component({
  selector: 'app-player-profile',
  standalone: true,
  imports: [TopbarComponent, AvatarComponent],
  templateUrl: './player-profile.html',
  styleUrl: './player-profile.scss',
})
export class PlayerProfile implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly rankingApi = inject(RankingService);
  private readonly location = inject(Location);

  readonly user = signal<PublicRankingUser | null>(null);
  readonly rankings = signal<RankingSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly joined = computed(() => {
    const iso = this.user()?.createdAt;
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
  });

  readonly competitiveRows = computed(() =>
    this.rankings()
      .filter((r) => MULTIPLAYER_MODES.includes(r.mode))
      .map((r) => {
        const total = r.wins + r.losses;
        return {
          mode: MODE_LABEL[r.mode] ?? r.mode,
          rating: r.rating,
          rank: r.rank,
          winRate: total === 0 ? '0%' : `${Math.round((r.wins / total) * 100)}%`,
          record: `${r.wins}/${r.losses}`,
          winStreak: r.winStreak,
        };
      })
  );

  ngOnInit(): void {
    const userId = this.route.snapshot.paramMap.get('userId');
    if (!userId) {
      this.error.set('ID de usuario no válido.');
      this.loading.set(false);
      return;
    }
    this.rankingApi.userRanking(userId).subscribe({
      next: (data) => {
        this.user.set(data.user);
        this.rankings.set(data.rankings ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar el perfil de este jugador.');
        this.loading.set(false);
      },
    });
  }

  goBack(): void {
    this.location.back();
  }

  modeLabel(mode: GameMode): string {
    return MODE_LABEL[mode] ?? mode;
  }
}
