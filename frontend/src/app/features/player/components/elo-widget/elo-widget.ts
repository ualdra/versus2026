import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RankingService } from '../../../../core/services/ranking.service';
import { GameMode } from '../../../../core/models/game.models';
import { RankingSummary } from '../../../../core/models/ranking.models';

const MODE_LABELS: Record<GameMode, string> = {
  SURVIVAL: 'Supervivencia',
  PRECISION: 'Precision',
  BINARY_DUEL: 'Duelo binario',
  PRECISION_DUEL: 'Duelo de precision',
  SABOTAGE: 'Sabotaje',
};

@Component({
  selector: 'app-elo-widget',
  standalone: true,
  templateUrl: './elo-widget.html',
  styleUrl: './elo-widget.scss',
})
export class EloWidgetComponent implements OnInit {
  private readonly rankings = inject(RankingService);

  readonly loading = signal(true);
  readonly rows = signal<RankingSummary[]>([]);
  readonly orderedRows = computed(() => {
    const byMode = new Map(this.rows().map((row) => [row.mode, row]));
    const modes: GameMode[] = ['BINARY_DUEL', 'PRECISION_DUEL', 'SABOTAGE'];
    return modes.map((mode) => byMode.get(mode)).filter(Boolean) as RankingSummary[];
  });

  ngOnInit(): void {
    this.rankings.mine().subscribe({
      next: (rows) => {
        this.rows.set(rows ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.rows.set([]);
        this.loading.set(false);
      },
    });
  }

  labelFor(mode: GameMode): string {
    return MODE_LABELS[mode];
  }

  trendClass(delta: number): string {
    if (delta > 0) return 'is-up';
    if (delta < 0) return 'is-down';
    return 'is-flat';
  }

  trendText(delta: number): string {
    if (delta > 0) return `+${delta}`;
    return String(delta);
  }
}
