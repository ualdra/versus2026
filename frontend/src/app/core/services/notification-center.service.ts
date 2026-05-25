import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import type { Achievement, AchievementUnlockedEvent } from '../models/achievement.models';
import { MODE_LABELS } from '../models/match.models';
import type { MatchFoundPayload } from '../models/match.models';
import {
  DEFAULT_NOTIFICATION_PREFS,
  NOTIFICATION_PREFS_KEY,
  type NotificationItem,
  type NotificationPrefs,
} from '../models/notification.models';
import type { MatchEvent } from '../models/ws.models';
import { AuthService } from './auth.service';
import { AchievementToastService } from './achievement-toast.service';
import { WebSocketService } from './websocket.service';

const MAX_NOTIFICATIONS = 30;

@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly auth = inject(AuthService);
  private readonly ws = inject(WebSocketService);
  private readonly achievementToasts = inject(AchievementToastService);

  private readonly _items = signal<NotificationItem[]>([]);
  private subscriptions = new Subscription();
  private currentUserId: string | null = null;
  private nextId = 1;
  private started = false;

  readonly items = this._items.asReadonly();
  readonly unreadCount = computed(() => this._items().filter((item) => !item.read).length);

  start(): void {
    const user = this.auth.user();
    if (!user) return;

    if (this.started && this.currentUserId === user.id) return;
    const userChanged = this.currentUserId !== null && this.currentUserId !== user.id;
    this.stop(userChanged);

    this.currentUserId = user.id;
    this._items.set(this.readStored(user.id));
    this.ws.connect();
    this.subscriptions.add(
      this.ws
        .subscribe<AchievementUnlockedEvent>('/user/queue/achievements')
        .subscribe((event) => this.handleAchievement(event)),
    );
    this.subscriptions.add(
      this.ws
        .subscribe<MatchEvent<unknown>>('/user/queue/match')
        .subscribe((event) => this.handleMatch(event)),
    );
    this.started = true;
  }

  stop(disconnect = true): void {
    this.subscriptions.unsubscribe();
    this.subscriptions = new Subscription();
    this.started = false;
    this.currentUserId = null;
    if (disconnect) {
      this.ws.disconnect();
    }
  }

  addAchievements(achievements: Achievement[] | null | undefined): void {
    for (const achievement of achievements ?? []) {
      this.addAchievement(achievement);
    }
  }

  markRead(id: string): void {
    this.updateItems((items) =>
      items.map((item) => (item.id === id ? { ...item, read: true } : item)),
    );
  }

  markAllRead(): void {
    this.updateItems((items) => items.map((item) => ({ ...item, read: true })));
  }

  clear(): void {
    this.updateItems(() => []);
  }

  private handleAchievement(event: AchievementUnlockedEvent): void {
    if (event.type !== 'ACHIEVEMENT_UNLOCKED') return;
    if (this.addAchievement(event.achievement)) {
      this.achievementToasts.show(event.achievement);
    }
  }

  private handleMatch(event: MatchEvent<unknown>): void {
    if (event.type !== 'MATCH_FOUND') return;
    if (!this.readPrefs().matchInvites) return;

    const payload = event.payload as Partial<MatchFoundPayload>;
    const mode = payload.mode ? MODE_LABELS[payload.mode] ?? payload.mode : 'partida';
    const opponents = payload.opponents?.map((opponent) => opponent.username).filter(Boolean) ?? [];
    const message = opponents.length > 0
      ? `${opponents.join(', ')} te espera en ${mode}.`
      : `Tu partida de ${mode} esta lista.`;

    this.add({
      type: 'MATCH_FOUND',
      title: 'Rival encontrado',
      message,
      tone: 'info',
      route: `/play/lobby/${event.matchId}`,
      sourceId: `match-found:${event.matchId}`,
    });
  }

  private addAchievement(achievement: Achievement): boolean {
    if (!achievement.unlocked || !this.readPrefs().achievements) return false;
    return this.add({
      type: 'ACHIEVEMENT_UNLOCKED',
      title: 'Logro desbloqueado',
      message: achievement.name,
      tone: 'success',
      route: '/profile',
      sourceId: `achievement:${achievement.key}`,
    });
  }

  private add(item: Omit<NotificationItem, 'id' | 'createdAt' | 'read'>): boolean {
    if (!this.ensureUserContext()) return false;

    const sourceId = item.sourceId;
    const exists = sourceId
      ? this._items().some((current) => current.sourceId === sourceId && current.type === item.type)
      : false;
    if (exists) return false;

    const next: NotificationItem = {
      ...item,
      id: this.createId(item.type),
      createdAt: new Date().toISOString(),
      read: false,
    };
    this.updateItems((items) => [next, ...items].slice(0, MAX_NOTIFICATIONS));
    return true;
  }

  private updateItems(project: (items: NotificationItem[]) => NotificationItem[]): void {
    const next = project(this._items());
    this._items.set(next);
    this.persist(next);
  }

  private createId(type: NotificationItem['type']): string {
    return `${type.toLowerCase()}-${Date.now()}-${this.nextId++}`;
  }

  private storageKey(userId: string): string {
    return `vs.notifications.${userId}`;
  }

  private ensureUserContext(): boolean {
    const user = this.auth.user();
    if (!user) return false;
    if (this.currentUserId !== user.id) {
      this.currentUserId = user.id;
      this._items.set(this.readStored(user.id));
    }
    return true;
  }

  private readStored(userId: string): NotificationItem[] {
    const raw = localStorage.getItem(this.storageKey(userId));
    if (!raw) return [];
    try {
      const value = JSON.parse(raw) as NotificationItem[];
      return Array.isArray(value) ? value.slice(0, MAX_NOTIFICATIONS) : [];
    } catch {
      return [];
    }
  }

  private persist(items: NotificationItem[]): void {
    if (!this.currentUserId) return;
    localStorage.setItem(this.storageKey(this.currentUserId), JSON.stringify(items));
  }

  private readPrefs(): NotificationPrefs {
    const raw = localStorage.getItem(NOTIFICATION_PREFS_KEY);
    if (!raw) return DEFAULT_NOTIFICATION_PREFS;
    try {
      return { ...DEFAULT_NOTIFICATION_PREFS, ...JSON.parse(raw) };
    } catch {
      return DEFAULT_NOTIFICATION_PREFS;
    }
  }
}
