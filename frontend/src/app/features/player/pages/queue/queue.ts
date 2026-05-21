import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { MatchService } from '../../../../core/services/match.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import {
  GameMode,
  MODE_KEY_TO_MODE,
  MODE_LABELS,
} from '../../../../core/models/match.models';

@Component({
  selector: 'app-queue',
  standalone: true,
  imports: [TopbarComponent],
  templateUrl: './queue.html',
  styleUrl: './queue.scss',
})
export class Queue implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(MatchService);
  private readonly ws = inject(WebSocketService);

  readonly elapsed = signal(0);
  readonly mode = signal<GameMode | null>(null);
  readonly modeLabel = signal('');
  readonly status = signal<'connecting' | 'searching' | 'cancelling' | 'error'>('connecting');
  readonly errorMsg = signal<string | null>(null);

  private timerId?: ReturnType<typeof setInterval>;
  private foundSub?: Subscription;

  ngOnInit(): void {
    const key = this.route.snapshot.paramMap.get('mode') ?? '';
    const mode = MODE_KEY_TO_MODE[key];
    if (!mode) {
      this.router.navigate(['/play/select']);
      return;
    }
    this.mode.set(mode);
    this.modeLabel.set(MODE_LABELS[mode]);

    this.ws.connect();
    this.foundSub = this.api.matchFound$().subscribe((event) => {
      const matchId = event.payload.matchId;
      this.router.navigate(['/play/lobby', matchId]);
    });

    this.api.joinQueue(mode).subscribe({
      next: () => {
        this.status.set('searching');
        this.startTimer();
      },
      error: (err) => {
        console.error('[queue] join failed', err);
        this.status.set('error');
        this.errorMsg.set(err?.error?.message ?? 'No se pudo entrar en la cola.');
      },
    });
  }

  ngOnDestroy(): void {
    this.stopTimer();
    this.foundSub?.unsubscribe();
  }

  cancel(): void {
    this.status.set('cancelling');
    this.api.leaveQueue().subscribe({
      next: () => this.router.navigate(['/play/select']),
      error: () => this.router.navigate(['/play/select']),
    });
  }

  formatElapsed(): string {
    const s = this.elapsed();
    const mm = String(Math.floor(s / 60)).padStart(2, '0');
    const ss = String(s % 60).padStart(2, '0');
    return `${mm}:${ss}`;
  }

  private startTimer(): void {
    this.timerId = setInterval(() => this.elapsed.update((v) => v + 1), 1000);
  }

  private stopTimer(): void {
    if (this.timerId) clearInterval(this.timerId);
    this.timerId = undefined;
  }
}
