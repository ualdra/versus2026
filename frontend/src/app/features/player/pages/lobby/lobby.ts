import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AuthService } from '../../../../core/services/auth.service';
import { MatchService } from '../../../../core/services/match.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import {
  LobbyState,
  MODE_LABELS,
  MatchStartingPayload,
  PlayerInLobby,
  PlayerJoinedPayload,
  PlayerLeftPayload,
  PlayerReadyPayload,
} from '../../../../core/models/match.models';
import { MatchEvent } from '../../../../core/models/ws.models';

type LobbyStatus = 'loading' | 'lobby' | 'starting' | 'started' | 'left' | 'error';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [RouterLink, TopbarComponent],
  templateUrl: './lobby.html',
  styleUrl: './lobby.scss',
})
export class Lobby implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly api = inject(MatchService);
  private readonly ws = inject(WebSocketService);

  readonly matchId = signal<string | null>(null);
  readonly lobby = signal<LobbyState | null>(null);
  readonly status = signal<LobbyStatus>('loading');
  readonly countdown = signal<number | null>(null);
  readonly errorMsg = signal<string | null>(null);

  readonly selfUserId = computed(() => this.auth.user()?.id ?? null);
  readonly selfPlayer = computed<PlayerInLobby | null>(() => {
    const id = this.selfUserId();
    return this.lobby()?.players.find((p) => p.userId === id) ?? null;
  });
  readonly opponents = computed<PlayerInLobby[]>(() => {
    const id = this.selfUserId();
    return this.lobby()?.players.filter((p) => p.userId !== id) ?? [];
  });
  readonly opponentSlot = computed<PlayerInLobby | null>(() => this.opponents()[0] ?? null);
  readonly selfReady = computed(() => this.selfPlayer()?.ready ?? false);
  readonly modeLabel = computed(() => {
    const mode = this.lobby()?.mode;
    return mode ? MODE_LABELS[mode] : '';
  });

  private eventsSub?: Subscription;
  private countdownTimer?: ReturnType<typeof setInterval>;
  private leftRedirectTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('matchId');
    if (!id) {
      this.router.navigate(['/play/select']);
      return;
    }
    this.matchId.set(id);
    this.ws.connect();

    this.api.getLobby(id).subscribe({
      next: (state) => {
        this.lobby.set(state);
        this.status.set(state.status === 'IN_PROGRESS' ? 'started' : 'lobby');
      },
      error: (err) => {
        console.error('[lobby] getLobby failed', err);
        this.status.set('error');
        this.errorMsg.set(err?.error?.message ?? 'Sala no disponible.');
      },
    });

    this.eventsSub = this.api.lobbyEvents$(id).subscribe((event) => this.handleEvent(event));
  }

  ngOnDestroy(): void {
    this.eventsSub?.unsubscribe();
    this.stopCountdown();
    if (this.leftRedirectTimer) clearTimeout(this.leftRedirectTimer);
  }

  toggleReady(): void {
    const id = this.matchId();
    if (!id || this.status() !== 'lobby') return;
    if (this.selfReady()) this.api.sendUnready(id);
    else this.api.sendReady(id);
  }

  cancel(): void {
    const id = this.matchId();
    if (!id) {
      this.router.navigate(['/play/select']);
      return;
    }
    this.api.abandonMatch(id).subscribe({
      next: () => this.router.navigate(['/play/select']),
      error: () => this.router.navigate(['/play/select']),
    });
  }

  private handleEvent(event: MatchEvent<unknown>): void {
    switch (event.type) {
      case 'PLAYER_JOINED':
        this.onPlayerJoined((event.payload as PlayerJoinedPayload).player);
        break;
      case 'PLAYER_LEFT':
        this.onPlayerLeft((event.payload as PlayerLeftPayload).userId);
        break;
      case 'PLAYER_READY': {
        const p = event.payload as PlayerReadyPayload;
        this.onPlayerReady(p.userId, p.ready);
        break;
      }
      case 'MATCH_STARTING':
        this.onMatchStarting((event.payload as MatchStartingPayload).countdownSeconds);
        break;
      case 'MATCH_START':
        this.onMatchStart();
        break;
    }
  }

  private onPlayerJoined(player: PlayerInLobby): void {
    const current = this.lobby();
    if (!current) return;
    if (current.players.some((p) => p.userId === player.userId)) return;
    this.lobby.set({ ...current, players: [...current.players, player] });
  }

  private onPlayerLeft(userId: string): void {
    const current = this.lobby();
    if (!current) return;
    const filtered = current.players.filter((p) => p.userId !== userId);
    this.lobby.set({ ...current, players: filtered });

    if (userId !== this.selfUserId() && this.status() !== 'started') {
      this.stopCountdown();
      this.status.set('left');
      this.leftRedirectTimer = setTimeout(() => this.router.navigate(['/play/select']), 2000);
    }
  }

  private onPlayerReady(userId: string, ready: boolean): void {
    const current = this.lobby();
    if (!current) return;
    const players = current.players.map((p) => (p.userId === userId ? { ...p, ready } : p));
    this.lobby.set({ ...current, players });
  }

  private onMatchStarting(seconds: number): void {
    this.status.set('starting');
    this.countdown.set(seconds);
    this.stopCountdown();
    this.countdownTimer = setInterval(() => {
      const next = (this.countdown() ?? 1) - 1;
      if (next <= 0) {
        this.countdown.set(0);
        this.stopCountdown();
      } else {
        this.countdown.set(next);
      }
    }, 1000);
  }

  private onMatchStart(): void {
    this.stopCountdown();
    this.status.set('started');
    // TODO PR #91/#92/#93: navegar a /play/binary-duel/:matchId, /play/precision-duel/:matchId,
    // o /play/sabotage/:matchId según `lobby()?.mode`.
  }

  private stopCountdown(): void {
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.countdownTimer = undefined;
  }
}
