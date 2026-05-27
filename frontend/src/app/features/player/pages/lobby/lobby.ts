import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';
import { AuthService } from '../../../../core/services/auth.service';
import { MatchService } from '../../../../core/services/match.service';
import { SocialService } from '../../../../core/services/social.service';
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
import { Friend } from '../../../../core/models/social.models';
import { MatchEvent } from '../../../../core/models/ws.models';

type LobbyStatus = 'loading' | 'lobby' | 'starting' | 'started' | 'left' | 'error';
type InviteStatus = 'idle' | 'loading' | 'sending';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [RouterLink, TopbarComponent, AvatarComponent],
  templateUrl: './lobby.html',
  styleUrl: './lobby.scss',
})
export class Lobby implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly api = inject(MatchService);
  private readonly social = inject(SocialService);
  private readonly ws = inject(WebSocketService);

  readonly matchId = signal<string | null>(null);
  readonly lobby = signal<LobbyState | null>(null);
  readonly status = signal<LobbyStatus>('loading');
  readonly countdown = signal<number | null>(null);
  readonly errorMsg = signal<string | null>(null);
  readonly friends = signal<Friend[]>([]);
  readonly selectedFriendId = signal('');
  readonly inviteStatus = signal<InviteStatus>('idle');
  readonly inviteError = signal<string | null>(null);
  readonly inviteMessage = signal<string | null>(null);

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
  readonly availableInviteFriends = computed(() => {
    const playerIds = new Set(this.lobby()?.players.map((p) => p.userId) ?? []);
    return this.friends().filter((friend) => !playerIds.has(friend.userId));
  });
  readonly inviteBusy = computed(() => this.inviteStatus() !== 'idle');
  readonly canInviteFriends = computed(() => {
    const state = this.lobby();
    return Boolean(
      state?.roomCode
      && this.status() === 'lobby'
      && state.status === 'WAITING'
      && state.players.length < state.requiredPlayers,
    );
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
        this.syncSelectedFriend();
      },
      error: (err) => {
        console.error('[lobby] getLobby failed', err);
        this.status.set('error');
        this.errorMsg.set(err?.error?.message ?? 'Sala no disponible.');
      },
    });

    this.eventsSub = this.api.lobbyEvents$(id).subscribe((event) => this.handleEvent(event));
    this.loadFriends();
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

  onFriendSelect(value: string): void {
    this.selectedFriendId.set(value);
    this.inviteError.set(null);
    this.inviteMessage.set(null);
  }

  inviteSelectedFriend(): void {
    const state = this.lobby();
    const friendId = this.selectedFriendId();
    if (!state || !friendId || this.inviteBusy() || !this.canInviteFriends()) return;

    this.inviteStatus.set('sending');
    this.inviteError.set(null);
    this.inviteMessage.set(null);
    this.social.inviteFriend(friendId, state.mode, state.matchId).subscribe({
      next: () => {
        const friendName = this.friends().find((friend) => friend.userId === friendId)?.username ?? 'tu amigo';
        this.inviteMessage.set(`Invitacion enviada a ${friendName}.`);
        this.inviteStatus.set('idle');
      },
      error: (err) => {
        this.inviteError.set(this.errorMessage(err, 'No se pudo enviar la invitacion.'));
        this.inviteStatus.set('idle');
      },
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
    this.syncSelectedFriend();
  }

  private onPlayerLeft(userId: string): void {
    const current = this.lobby();
    if (!current) return;
    const filtered = current.players.filter((p) => p.userId !== userId);
    this.lobby.set({ ...current, players: filtered });
    this.syncSelectedFriend();

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
    const mode = this.lobby()?.mode;
    const id = this.matchId();
    if (!mode || !id) return;
    const path =
      mode === 'BINARY_DUEL' ? `/play/binary-duel/${id}` :
      mode === 'PRECISION_DUEL' ? `/play/precision-duel/${id}` :
      mode === 'SABOTAGE' ? `/play/sabotage/${id}` : null;
    if (path) this.router.navigateByUrl(path);
  }

  private stopCountdown(): void {
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.countdownTimer = undefined;
  }

  private loadFriends(): void {
    this.inviteStatus.set('loading');
    this.social.friends().subscribe({
      next: (friends) => {
        this.friends.set(friends);
        this.inviteStatus.set('idle');
        this.syncSelectedFriend();
      },
      error: (err) => {
        this.inviteError.set(this.errorMessage(err, 'No se pudo cargar la lista de amigos.'));
        this.inviteStatus.set('idle');
      },
    });
  }

  private syncSelectedFriend(): void {
    const selected = this.selectedFriendId();
    const available = this.availableInviteFriends();
    if (selected && available.some((friend) => friend.userId === selected)) return;
    this.selectedFriendId.set(available[0]?.userId ?? '');
  }

  private errorMessage(err: unknown, fallback: string): string {
    if (typeof err === 'object' && err !== null && 'error' in err) {
      const httpError = err as { error?: { message?: string } };
      return httpError.error?.message ?? fallback;
    }
    return fallback;
  }
}
