import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { GameMode } from '../../../../core/models/match.models';

interface OpponentRecap {
  username: string;
  avatarUrl: string | null;
  score: number;
  bestStreak: number;
  livesRemaining: number;
  avgDeviation: number | null;
}

export interface ResultState {
  mode: GameMode;
  multiplayer: boolean;
  outcome: 'WIN' | 'LOSS' | 'DRAW';
  score: number;
  bestStreak: number;
  rounds: number;
  /** Mantengo `won` para compatibilidad con navegaciones previas */
  won?: boolean;
  livesRemaining?: number;
  avgDeviation?: number | null;
  opponent?: OpponentRecap;
  reason?: 'NORMAL' | 'DISCONNECT' | 'MAX_ROUNDS_TIE' | 'NO_QUESTION';
  sabotagesUsed?: number;
}

@Component({
  selector: 'app-result',
  standalone: true,
  imports: [RouterLink, TopbarComponent, DecimalPipe],
  templateUrl: './result.html',
  styleUrl: './result.scss',
})
export class Result {
  private readonly router = inject(Router);

  readonly state = signal<ResultState | null>(this.readState());
  readonly outcome = computed<'WIN' | 'LOSS' | 'DRAW'>(() => {
    const s = this.state();
    if (!s) return 'LOSS';
    if (s.outcome) return s.outcome;
    return s.won ? 'WIN' : 'LOSS';
  });
  readonly isWin = computed(() => this.outcome() === 'WIN');
  readonly isDraw = computed(() => this.outcome() === 'DRAW');

  readonly modeLabel = computed(() => {
    const m = this.state()?.mode ?? 'SURVIVAL';
    switch (m) {
      case 'PRECISION': return 'PRECISIÓN';
      case 'BINARY_DUEL': return 'DUELO BINARIO';
      case 'PRECISION_DUEL': return 'DUELO DE PRECISIÓN';
      case 'SABOTAGE': return 'SABOTAJE';
      default: return 'SUPERVIVENCIA';
    }
  });

  readonly verdict = computed(() => {
    const o = this.outcome();
    if (o === 'DRAW') return 'EMPATE';
    if (o === 'WIN') return this.state()?.multiplayer ? '¡VICTORIA!' : '¡EL ÚLTIMO EN PIE!';
    return 'GAME OVER';
  });

  readonly reasonNote = computed(() => {
    const r = this.state()?.reason;
    if (r === 'DISCONNECT') return 'Tu rival se desconectó.';
    if (r === 'MAX_ROUNDS_TIE') return 'Se alcanzó el límite de rondas.';
    if (r === 'NO_QUESTION') return 'La partida no pudo continuar: no hay preguntas disponibles para este modo.';
    return null;
  });

  readonly stats = computed(() => {
    const s = this.state();
    if (!s) {
      return [
        { num: '—', label: 'Racha máxima', accent: 'var(--vs-accent-gold)' },
        { num: '—', label: 'Puntos',       accent: 'var(--vs-accent-green)' },
        { num: '—', label: 'Rondas' },
      ];
    }
    return [
      { num: String(s.bestStreak), label: 'Racha máxima', accent: 'var(--vs-accent-gold)' },
      { num: s.score.toLocaleString('es-ES'), label: 'Puntos', accent: 'var(--vs-accent-green)' },
      { num: String(s.rounds), label: 'Rondas' },
    ];
  });

  readonly replayLink = computed(() => {
    const s = this.state();
    if (!s) return '/play/select';
    if (s.multiplayer) return '/play/select';
    return s.mode === 'PRECISION' ? '/play/precision' : '/play/survival';
  });

  private readState(): ResultState | null {
    const nav = this.router.getCurrentNavigation();
    const fromNav = nav?.extras?.state as ResultState | undefined;
    if (fromNav) return fromNav;
    const fromHistory = (history.state ?? null) as ResultState | null;
    if (fromHistory && typeof fromHistory.score === 'number') return fromHistory;
    return null;
  }
}
