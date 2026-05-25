import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { EMPTY, Subject } from 'rxjs';
import type { Achievement, AchievementUnlockedEvent } from '../models/achievement.models';
import type { AuthUser } from '../models/auth.models';
import {
  DEFAULT_NOTIFICATION_PREFS,
  NOTIFICATION_PREFS_KEY,
} from '../models/notification.models';
import type { MatchEvent } from '../models/ws.models';
import { AchievementToastService } from './achievement-toast.service';
import { AuthService } from './auth.service';
import { NotificationCenterService } from './notification-center.service';
import { WebSocketService } from './websocket.service';

describe('NotificationCenterService', () => {
  let service: NotificationCenterService;
  let achievementEvents: Subject<AchievementUnlockedEvent>;
  let matchEvents: Subject<MatchEvent<unknown>>;
  let toastService: { show: ReturnType<typeof vi.fn> };
  let webSocketService: {
    connect: ReturnType<typeof vi.fn>;
    disconnect: ReturnType<typeof vi.fn>;
    subscribe: ReturnType<typeof vi.fn>;
  };

  const authUser = signal<AuthUser | null>({
    id: 'user-1',
    username: 'player',
    role: 'PLAYER',
    avatarUrl: null,
  });

  const achievement: Achievement = {
    id: 'achievement-1',
    key: 'first_game',
    name: 'Primeros pasos',
    description: 'Juega tu primera partida.',
    iconKey: 'first',
    category: 'Primeros pasos',
    unlocked: true,
    unlockedAt: '2026-01-02T00:00:00Z',
  };

  beforeEach(() => {
    localStorage.clear();
    authUser.set({
      id: 'user-1',
      username: 'player',
      role: 'PLAYER',
      avatarUrl: null,
    });
    achievementEvents = new Subject<AchievementUnlockedEvent>();
    matchEvents = new Subject<MatchEvent<unknown>>();
    toastService = { show: vi.fn() };
    webSocketService = {
      connect: vi.fn(),
      disconnect: vi.fn(),
      subscribe: vi.fn((destination: string) => {
        if (destination === '/user/queue/achievements') return achievementEvents.asObservable();
        if (destination === '/user/queue/match') return matchEvents.asObservable();
        return EMPTY;
      }),
    };

    TestBed.configureTestingModule({
      providers: [
        NotificationCenterService,
        {
          provide: AuthService,
          useValue: {
            user: authUser,
          },
        },
        {
          provide: AchievementToastService,
          useValue: toastService,
        },
        {
          provide: WebSocketService,
          useValue: webSocketService,
        },
      ],
    });

    service = TestBed.inject(NotificationCenterService);
  });

  it('should add realtime achievement notifications once and show toast', () => {
    service.start();

    achievementEvents.next({ type: 'ACHIEVEMENT_UNLOCKED', achievement });
    achievementEvents.next({ type: 'ACHIEVEMENT_UNLOCKED', achievement });

    expect(service.items()).toHaveLength(1);
    expect(service.unreadCount()).toBe(1);
    expect(service.items()[0].message).toBe('Primeros pasos');
    expect(toastService.show).toHaveBeenCalledTimes(1);
    expect(JSON.parse(localStorage.getItem('vs.notifications.user-1') ?? '[]')).toHaveLength(1);
  });

  it('should add match found notifications when match invites are enabled', () => {
    service.start();

    matchEvents.next({
      type: 'MATCH_FOUND',
      matchId: 'match-1',
      payload: {
        matchId: 'match-1',
        mode: 'BINARY_DUEL',
        opponents: [{ userId: 'user-2', username: 'rival', avatarUrl: null, ready: false }],
      },
    });

    expect(service.items()).toHaveLength(1);
    expect(service.items()[0].title).toBe('Rival encontrado');
    expect(service.items()[0].route).toBe('/play/lobby/match-1');
  });

  it('should ignore disabled notification categories', () => {
    localStorage.setItem(NOTIFICATION_PREFS_KEY, JSON.stringify({
      ...DEFAULT_NOTIFICATION_PREFS,
      achievements: false,
      matchInvites: false,
    }));

    service.start();
    achievementEvents.next({ type: 'ACHIEVEMENT_UNLOCKED', achievement });
    matchEvents.next({
      type: 'MATCH_FOUND',
      matchId: 'match-1',
      payload: {
        matchId: 'match-1',
        mode: 'BINARY_DUEL',
        opponents: [],
      },
    });

    expect(service.items()).toEqual([]);
    expect(toastService.show).not.toHaveBeenCalled();
  });

  it('should mark and clear notifications', () => {
    service.addAchievements([achievement]);

    const id = service.items()[0].id;
    service.markRead(id);
    expect(service.items()[0].read).toBe(true);

    service.clear();
    expect(service.items()).toEqual([]);
  });

  it('should reconnect websocket when the authenticated user changes', () => {
    service.start();

    authUser.set({
      id: 'user-2',
      username: 'other',
      role: 'PLAYER',
      avatarUrl: null,
    });
    service.start();

    expect(webSocketService.disconnect).toHaveBeenCalledTimes(1);
    expect(webSocketService.connect).toHaveBeenCalledTimes(2);
  });
});
