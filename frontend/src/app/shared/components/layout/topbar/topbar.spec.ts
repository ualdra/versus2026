import { ComponentFixture, TestBed } from '@angular/core/testing';

import { type Signal, type WritableSignal, computed, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TopbarComponent } from './topbar';
import { AuthService } from '../../../../core/services/auth.service';
import { AchievementService } from '../../../../core/services/achievement.service';
import { UserService } from '../../../../core/services/user.service';
import { StatsService } from '../../../../core/services/stats.service';
import { PlayerStats } from '../../../../core/models/game.models';
import { NotificationItem } from '../../../../core/models/notification.models';
import { NotificationCenterService } from '../../../../core/services/notification-center.service';

describe('TopbarComponent', () => {
  let fixture: ComponentFixture<TopbarComponent>;
  let notificationItems: WritableSignal<NotificationItem[]>;
  let notificationService: {
    items: Signal<NotificationItem[]>;
    unreadCount: Signal<number>;
    start: ReturnType<typeof vi.fn>;
    markRead: ReturnType<typeof vi.fn>;
    markAllRead: ReturnType<typeof vi.fn>;
    clear: ReturnType<typeof vi.fn>;
  };

  const authUser = signal({
    id: 'user-1',
    username: 'player',
    role: 'PLAYER' as const,
    avatarUrl: 'data:image/png;base64,abc',
  });

  const stats: PlayerStats[] = [
    {
      mode: 'SURVIVAL',
      gamesPlayed: 2,
      gamesWon: 1,
      winRate: 50,
      bestStreak: 3,
      currentStreak: 1,
      avgDeviation: null,
    },
  ];

  beforeEach(async () => {
    notificationItems = signal<NotificationItem[]>([]);
    notificationService = {
      items: notificationItems,
      unreadCount: computed(() => notificationItems().filter((item) => !item.read).length),
      start: vi.fn(),
      markRead: vi.fn((id: string) => {
        notificationItems.update((items) =>
          items.map((item) => (item.id === id ? { ...item, read: true } : item)),
        );
      }),
      markAllRead: vi.fn(() => {
        notificationItems.update((items) => items.map((item) => ({ ...item, read: true })));
      }),
      clear: vi.fn(() => notificationItems.set([])),
    };

    await TestBed.configureTestingModule({
      imports: [TopbarComponent],
      providers: [
        provideRouter([]),

        {
          provide: AuthService,
          useValue: {
            user: authUser,
            isAuthenticated: () => true,
            updateCachedUser: vi.fn(),
          },
        },
        {
          provide: UserService,
          useValue: {
            me: () => of({
              id: 'user-1',
              username: 'player',
              email: 'player@versus.com',
              avatarUrl: 'data:image/png;base64,abc',
              role: 'PLAYER',
              createdAt: '2026-01-01T00:00:00Z',
            }),
          },
        },
        {
          provide: StatsService,
          useValue: {
            mine: () => of(stats),
          },
        },
        {
          provide: AchievementService,
          useValue: {
            list: () => of([
              {
                id: 'achievement-1',
                key: 'first_game',
                name: 'Primeros pasos',
                description: 'Juega tu primera partida.',
                iconKey: 'first',
                category: 'Primeros pasos',
                unlocked: true,
                unlockedAt: '2026-01-02T00:00:00Z',
              },
            ]),
          },
        },
        { provide: NotificationCenterService, useValue: notificationService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TopbarComponent);
    fixture.detectChanges();

    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('should render cached username, avatar and calculated XP', () => {
    const text = fixture.nativeElement.textContent;
    const img = fixture.nativeElement.querySelector('.vs-avatar img') as HTMLImageElement | null;

    expect(text).toContain('player');
    expect(text).toContain('325 XP');
    expect(text).toContain('1');
    expect(img?.getAttribute('src')).toBe('data:image/png;base64,abc');
  });

  it('should prefer explicit user input over cached profile', () => {
    fixture.componentRef.setInput('user', {
      name: 'override',
      xp: 999,
      avatarUrl: 'https://avatar.test/a.svg',
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    const img = fixture.nativeElement.querySelector('.vs-avatar img') as HTMLImageElement | null;

    expect(text).toContain('override');
    expect(text).toContain('999 XP');
    expect(img?.getAttribute('src')).toBe('https://avatar.test/a.svg');

  });

  it('should render notification center and mark selected notification as read', () => {
    notificationItems.set([
      {
        id: 'n1',
        type: 'MATCH_FOUND',
        title: 'Rival encontrado',
        message: 'player2 te espera en Duelo binario.',
        createdAt: new Date().toISOString(),
        read: false,
        tone: 'info',
        sourceId: 'match-found:match-1',
      },
    ]);
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector('.vs-notification-center__trigger') as HTMLButtonElement;
    expect(fixture.nativeElement.querySelector('.vs-notification-center__badge')?.textContent).toContain('1');

    trigger.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Rival encontrado');
    const item = fixture.nativeElement.querySelector('.vs-notification-item') as HTMLElement;
    item.click();

    expect(notificationService.markRead).toHaveBeenCalledWith('n1');
  });
});
