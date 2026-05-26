import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { GameMode, MODE_LABELS } from '../../../../core/models/match.models';
import {
  Friend,
  FriendRequest,
  MatchInvite,
  SocialRelation,
  SocialUser,
} from '../../../../core/models/social.models';
import { SocialService } from '../../../../core/services/social.service';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';

type SocialMode = 'idle' | 'loading' | 'working';

@Component({
  selector: 'app-friends',
  standalone: true,
  imports: [TopbarComponent, AvatarComponent],
  templateUrl: './friends.html',
  styleUrl: './friends.scss',
})
export class Friends implements OnInit {
  private readonly social = inject(SocialService);
  private readonly router = inject(Router);

  readonly friends = signal<Friend[]>([]);
  readonly incomingRequests = signal<FriendRequest[]>([]);
  readonly outgoingRequests = signal<FriendRequest[]>([]);
  readonly incomingInvites = signal<MatchInvite[]>([]);
  readonly outgoingInvites = signal<MatchInvite[]>([]);
  readonly searchResults = signal<SocialUser[]>([]);
  readonly searchTerm = signal('');
  readonly selectedInviteMode = signal<GameMode>('BINARY_DUEL');
  readonly status = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly mode = signal<SocialMode>('loading');

  readonly busy = computed(() => this.mode() !== 'idle');
  readonly hasPending = computed(() =>
    this.incomingRequests().length + this.incomingInvites().length > 0,
  );

  readonly inviteModes: GameMode[] = ['BINARY_DUEL', 'PRECISION_DUEL', 'SABOTAGE'];
  readonly modeLabels = MODE_LABELS;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.mode.set('loading');
    this.error.set(null);
    let pending = 5;
    const done = () => {
      pending -= 1;
      if (pending === 0) this.mode.set('idle');
    };
    const failDone = (message: string) => {
      this.error.set(message);
      done();
    };

    this.social.friends().subscribe({
      next: (items) => this.friends.set(items),
      error: () => failDone('No se pudo cargar tu lista de amigos.'),
      complete: done,
    });
    this.social.incomingFriendRequests().subscribe({
      next: (items) => this.incomingRequests.set(items),
      error: () => failDone('No se pudieron cargar las solicitudes.'),
      complete: done,
    });
    this.social.outgoingFriendRequests().subscribe({
      next: (items) => this.outgoingRequests.set(items),
      error: () => failDone('No se pudieron cargar las solicitudes enviadas.'),
      complete: done,
    });
    this.social.incomingMatchInvites().subscribe({
      next: (items) => this.incomingInvites.set(items),
      error: () => failDone('No se pudieron cargar las invitaciones.'),
      complete: done,
    });
    this.social.outgoingMatchInvites().subscribe({
      next: (items) => this.outgoingInvites.set(items),
      error: () => failDone('No se pudieron cargar las invitaciones enviadas.'),
      complete: done,
    });
  }

  onSearchInput(value: string): void {
    this.searchTerm.set(value);
    if (value.trim().length < 2) {
      this.searchResults.set([]);
    }
  }

  search(): void {
    const query = this.searchTerm().trim();
    if (query.length < 2 || this.busy()) return;
    this.mode.set('working');
    this.error.set(null);
    this.social.searchUsers(query).subscribe({
      next: (items) => {
        this.searchResults.set(items);
        this.mode.set('idle');
      },
      error: () => this.fail('No se pudo buscar jugadores.'),
    });
  }

  sendRequest(user: SocialUser): void {
    if (this.busy() || user.relation !== 'NONE') return;
    this.mode.set('working');
    this.social.sendFriendRequest(user.userId).subscribe({
      next: () => {
        this.status.set('Solicitud enviada.');
        this.searchResults.update((items) =>
          items.map((item) => item.userId === user.userId ? { ...item, relation: 'REQUEST_SENT' } : item),
        );
        this.refresh();
      },
      error: () => this.fail('No se pudo enviar la solicitud.'),
    });
  }

  acceptRequest(request: FriendRequest): void {
    if (this.busy()) return;
    this.mode.set('working');
    this.social.acceptFriendRequest(request.id).subscribe({
      next: () => {
        this.status.set('Solicitud aceptada.');
        this.refresh();
      },
      error: () => this.fail('No se pudo aceptar la solicitud.'),
    });
  }

  declineRequest(request: FriendRequest): void {
    if (this.busy()) return;
    this.mode.set('working');
    this.social.declineFriendRequest(request.id).subscribe({
      next: () => {
        this.status.set('Solicitud rechazada.');
        this.refresh();
      },
      error: () => this.fail('No se pudo rechazar la solicitud.'),
    });
  }

  cancelRequest(request: FriendRequest): void {
    if (this.busy()) return;
    this.mode.set('working');
    this.social.cancelFriendRequest(request.id).subscribe({
      next: () => {
        this.status.set('Solicitud cancelada.');
        this.refresh();
      },
      error: () => this.fail('No se pudo cancelar la solicitud.'),
    });
  }

  invite(friend: Friend): void {
    if (this.busy()) return;
    this.mode.set('working');
    this.social.inviteFriend(friend.userId, this.selectedInviteMode()).subscribe({
      next: (invite) => this.router.navigate(['/play/lobby', invite.matchId]),
      error: () => this.fail('No se pudo crear la invitacion.'),
    });
  }

  acceptInvite(invite: MatchInvite): void {
    if (this.busy()) return;
    this.mode.set('working');
    this.social.acceptMatchInvite(invite.id).subscribe({
      next: (lobby) => this.router.navigate(['/play/lobby', lobby.matchId]),
      error: () => this.fail('No se pudo aceptar la invitacion.'),
    });
  }

  declineInvite(invite: MatchInvite): void {
    if (this.busy()) return;
    this.mode.set('working');
    this.social.declineMatchInvite(invite.id).subscribe({
      next: () => {
        this.status.set('Invitacion rechazada.');
        this.refresh();
      },
      error: () => this.fail('No se pudo rechazar la invitacion.'),
    });
  }

  setInviteMode(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as GameMode;
    this.selectedInviteMode.set(value);
  }

  relationLabel(relation: SocialRelation): string {
    const labels: Record<SocialRelation, string> = {
      SELF: 'Tú',
      NONE: 'Enviar',
      FRIEND: 'Amigo',
      REQUEST_SENT: 'Pendiente',
      REQUEST_RECEIVED: 'Recibida',
    };
    return labels[relation];
  }

  private fail(message: string): void {
    this.mode.set('idle');
    this.error.set(message);
  }
}
