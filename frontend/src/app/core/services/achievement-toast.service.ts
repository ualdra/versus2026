import { Injectable, signal } from '@angular/core';
import { Achievement } from '../models/achievement.models';
import {
  DEFAULT_NOTIFICATION_PREFS,
  NOTIFICATION_PREFS_KEY,
} from '../models/notification.models';

export interface AchievementToast {
  id: number;
  achievement: Achievement;
}

@Injectable({ providedIn: 'root' })
export class AchievementToastService {
  private readonly recentKeys = new Map<string, number>();
  private nextId = 1;

  readonly items = signal<AchievementToast[]>([]);

  showMany(achievements: Achievement[] | null | undefined): void {
    for (const achievement of achievements ?? []) {
      this.show(achievement);
    }
  }

  show(achievement: Achievement): void {
    if (!achievement.unlocked) return;
    if (!this.achievementNotificationsEnabled()) return;

    const now = Date.now();
    const lastSeen = this.recentKeys.get(achievement.key);
    if (lastSeen && now - lastSeen < 3500) return;
    this.recentKeys.set(achievement.key, now);

    const id = this.nextId++;
    this.items.update((items) => [...items, { id, achievement }]);
    setTimeout(() => this.dismiss(id), 5000);
  }

  dismiss(id: number): void {
    this.items.update((items) => items.filter((item) => item.id !== id));
  }

  private achievementNotificationsEnabled(): boolean {
    const raw = localStorage.getItem(NOTIFICATION_PREFS_KEY);
    if (!raw) return DEFAULT_NOTIFICATION_PREFS.achievements;
    try {
      return { ...DEFAULT_NOTIFICATION_PREFS, ...JSON.parse(raw) }.achievements;
    } catch {
      return DEFAULT_NOTIFICATION_PREFS.achievements;
    }
  }
}
