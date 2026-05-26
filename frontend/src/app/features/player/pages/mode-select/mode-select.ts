import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { GameMode } from '../../../../core/models/match.models';
import { MatchService } from '../../../../core/services/match.service';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';

type PrivateRoomStatus = 'idle' | 'creating' | 'joining';
type ModeCard = {
  key: string;
  name: string;
  icon: string;
  color: string;
  desc: string;
  tag: string;
  route: string;
  featured?: boolean;
  mode?: GameMode;
};

@Component({
  selector: 'app-mode-select',
  standalone: true,
  imports: [RouterLink, TopbarComponent],
  templateUrl: './mode-select.html',
  styleUrl: './mode-select.scss',
})
export class ModeSelect {
  private readonly router = inject(Router);
  private readonly matchService = inject(MatchService);

  readonly modes: ModeCard[] = [
    {
      key: 'survival', name: 'SUPERVIVENCIA', icon: '☠',
      color: 'var(--vs-accent-red)',
      desc: 'Dos opciones. Una es correcta. La otra te quita una vida. 3 vidas y a aguantar.',
      tag: 'Solo · 1J', featured: true,
      route: '/play/survival',
    },
    {
      key: 'precision', name: 'PRECISIÓN', icon: '◎',
      color: 'var(--vs-accent-blue)',
      desc: 'Respuesta numérica. Cuanto más cerca, mejor. Acertar de cerca recupera vida.',
      tag: 'Solo · 1J',
      route: '/play/precision',
    },
    {
      key: 'binary', name: 'DUELO BINARIO', icon: '⚔',
      color: 'var(--vs-accent-gold)',
      desc: 'Supervivencia, pero con alguien respirándote en la nuca. Gana el último en pie.',
      tag: '2J · Online',
      route: '/play/queue/binary',
      mode: 'BINARY_DUEL',
    },
    {
      key: 'pduel', name: 'DUELO DE PRECISIÓN', icon: '⊕',
      color: 'var(--vs-accent-green)',
      desc: 'Modo Precisión a dos bandas. El primero a cero, fuera.',
      tag: '2J · Online',
      route: '/play/queue/pduel',
      mode: 'PRECISION_DUEL',
    },
    {
      key: 'sabotage', name: 'SABOTAJE', icon: '⚡',
      color: 'var(--vs-accent-purple)',
      desc: 'No pierdes vida fallando: se la quitas al rival si aciertas mejor.',
      tag: '2J · Online',
      route: '/play/queue/sabotage',
      mode: 'SABOTAGE',
    },
    {
      key: 'practice', name: 'PRÁCTICA', icon: '◆',
      color: 'var(--vs-accent-green)',
      desc: 'Sin presión: elige categoría, contesta y aprende. No hay vidas ni timer.',
      tag: 'Solo · Sin partida', featured: false,
      route: '/play/practice',
    },
  ];

  readonly multiplayerModes = this.modes.filter(
    (mode): mode is ModeCard & { mode: GameMode } => mode.mode !== undefined,
  );
  readonly selectedPrivateMode = signal<GameMode>('BINARY_DUEL');
  readonly roomCode = signal('');
  readonly privateStatus = signal<PrivateRoomStatus>('idle');
  readonly privateError = signal<string | null>(null);
  readonly privateBusy = computed(() => this.privateStatus() !== 'idle');

  selectPrivateMode(mode: GameMode): void {
    if (this.privateBusy()) return;
    this.selectedPrivateMode.set(mode);
    this.privateError.set(null);
  }

  createPrivateRoom(): void {
    if (this.privateBusy()) return;
    this.privateStatus.set('creating');
    this.privateError.set(null);

    this.matchService.createMatch(this.selectedPrivateMode()).subscribe({
      next: (match) => this.router.navigate(['/play/lobby', match.matchId]),
      error: (err) => this.failPrivateAction(err, 'No se pudo crear la sala privada.'),
    });
  }

  onRoomCodeInput(value: string): void {
    const normalized = value.toUpperCase().replace(/[\s-]/g, '').slice(0, 6);
    this.roomCode.set(normalized);
    if (this.privateError()) this.privateError.set(null);
  }

  joinPrivateRoom(): void {
    if (this.privateBusy()) return;
    const code = this.roomCode();
    if (code.length !== 6) {
      this.privateError.set('Introduce un código de 6 caracteres.');
      return;
    }

    this.privateStatus.set('joining');
    this.privateError.set(null);
    this.matchService.joinMatchByCode(code).subscribe({
      next: (lobby) => this.router.navigate(['/play/lobby', lobby.matchId]),
      error: (err) => this.failPrivateAction(err, 'No se pudo entrar en la sala.'),
    });
  }

  private failPrivateAction(err: unknown, fallback: string): void {
    this.privateStatus.set('idle');
    this.privateError.set(this.errorMessage(err, fallback));
  }

  private errorMessage(err: unknown, fallback: string): string {
    if (typeof err === 'object' && err !== null && 'error' in err) {
      const httpError = err as { error?: { message?: string } };
      return httpError.error?.message ?? fallback;
    }
    return fallback;
  }
}
