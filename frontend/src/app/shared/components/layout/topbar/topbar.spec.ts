import { ComponentFixture, TestBed } from '@angular/core/testing';

import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TopbarComponent } from './topbar';
import { AuthService } from '../../../../core/services/auth.service';
import { AchievementService } from '../../../../core/services/achievement.service';
import { UserService } from '../../../../core/services/user.service';
import { StatsService } from '../../../../core/services/stats.service';
import { PlayerStats } from '../../../../core/models/game.models';

describe('TopbarComponent', () => {
  let fixture: ComponentFixture<TopbarComponent>;

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
<<<<<<< HEAD
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
=======

>>>>>>> 4f1ef96b4c35b4922860ddf442521693c19495ce
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
});
