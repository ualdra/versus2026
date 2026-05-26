import { Component, EventEmitter, Input, OnChanges, Output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { StatsService } from '../../../../../core/services/stats.service';
import { GameMode, MatchDetail } from '../../../../../core/models/game.models';

const MODE_LABEL: Record<GameMode, string> = {
  SURVIVAL: 'Supervivencia',
  PRECISION: 'Precisión',
  BINARY_DUEL: 'Duelo binario',
  PRECISION_DUEL: 'Duelo de precisión',
  SABOTAGE: 'Sabotaje',
};

@Component({
  selector: 'app-match-detail-modal',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './match-detail-modal.html',
  styleUrl: './match-detail-modal.scss',
})
export class MatchDetailModal implements OnChanges {
  @Input() matchId: string | null = null;
  @Output() closed = new EventEmitter<void>();

  readonly detail = signal<MatchDetail | null>(null);
  readonly loading = signal(false);

  private readonly statsApi: StatsService;

  constructor(statsApi: StatsService) {
    this.statsApi = statsApi;
  }

  ngOnChanges(): void {
    if (this.matchId) {
      this.loading.set(true);
      this.detail.set(null);
      this.statsApi.matchDetail(this.matchId).subscribe({
        next: (d) => { this.detail.set(d); this.loading.set(false); },
        error: () => this.loading.set(false),
      });
    }
  }

  modeLabel(mode: GameMode): string {
    return MODE_LABEL[mode] ?? mode;
  }

  close(): void {
    this.closed.emit();
  }
}
