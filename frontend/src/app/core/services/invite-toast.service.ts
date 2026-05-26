import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  DEFAULT_NOTIFICATION_PREFS,
  NOTIFICATION_PREFS_KEY,
} from '../models/notification.models';
import { SocialService } from './social.service';

export interface InviteToast {
  id: number;
  title: string;
  message: string;
  inviteId: string;
  matchId: string;
  accepting: boolean;
}

@Injectable({ providedIn: 'root' })
export class InviteToastService {
  private readonly social = inject(SocialService);
  private readonly router = inject(Router);
  private nextId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  readonly items = signal<InviteToast[]>([]);

  show(title: string, message: string, inviteId: string, matchId: string): void {
    if (!this.inviteNotificationsEnabled()) return;

    const id = this.nextId++;
    this.items.update((items) => [...items, { id, title, message, inviteId, matchId, accepting: false }]);
    this.timers.set(id, setTimeout(() => this.dismiss(id), 8000));
  }

  dismiss(id: number): void {
    clearTimeout(this.timers.get(id));
    this.timers.delete(id);
    this.items.update((items) => items.filter((item) => item.id !== id));
  }

  accept(id: number): void {
    const toast = this.items().find((item) => item.id === id);
    if (!toast || toast.accepting) return;

    this.items.update((items) =>
      items.map((item) => (item.id === id ? { ...item, accepting: true } : item)),
    );

    this.social.acceptMatchInvite(toast.inviteId).subscribe({
      next: (lobby) => {
        this.dismiss(id);
        this.router.navigate(['/play/lobby', lobby.matchId]);
      },
      error: () => {
        this.items.update((items) =>
          items.map((item) => (item.id === id ? { ...item, accepting: false } : item)),
        );
      },
    });
  }

  private inviteNotificationsEnabled(): boolean {
    const raw = localStorage.getItem(NOTIFICATION_PREFS_KEY);
    if (!raw) return DEFAULT_NOTIFICATION_PREFS.matchInvites;
    try {
      return { ...DEFAULT_NOTIFICATION_PREFS, ...JSON.parse(raw) }.matchInvites;
    } catch {
      return DEFAULT_NOTIFICATION_PREFS.matchInvites;
    }
  }
}
