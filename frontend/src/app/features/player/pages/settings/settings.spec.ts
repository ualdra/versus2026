import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Settings } from './settings';
import { AuthService } from '../../../../core/services/auth.service';
import { AchievementService } from '../../../../core/services/achievement.service';
import { UserService } from '../../../../core/services/user.service';
import { StatsService } from '../../../../core/services/stats.service';

describe('Settings', () => {
  let component: Settings;
  let fixture: ComponentFixture<Settings>;
  let userService: {
    me: ReturnType<typeof vi.fn>;
    updateMe: ReturnType<typeof vi.fn>;
    changePassword: ReturnType<typeof vi.fn>;
    updateAvatarUrl: ReturnType<typeof vi.fn>;
    uploadAvatar: ReturnType<typeof vi.fn>;
    deleteMe: ReturnType<typeof vi.fn>;
  };

  const authUser = signal({
    id: 'user-1',
    username: 'player',
    role: 'PLAYER' as const,
    avatarUrl: null,
  });

  const me = {
    id: 'user-1',
    username: 'player',
    email: 'player@versus.com',
    avatarUrl: null,
    role: 'PLAYER' as const,
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    localStorage.clear();
    userService = {
      me: vi.fn(() => of(me)),
      updateMe: vi.fn(() => of(me)),
      changePassword: vi.fn(() => of(void 0)),
      updateAvatarUrl: vi.fn((avatarUrl: string) => of({ ...me, avatarUrl })),
      uploadAvatar: vi.fn(() => of(me)),
      deleteMe: vi.fn(() => of(void 0)),
    };

    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            user: authUser,
            isAuthenticated: () => true,
            updateCachedUser: vi.fn(),
            clear: vi.fn(),
          },
        },
        { provide: UserService, useValue: userService },
        { provide: StatsService, useValue: { mine: () => of([]) } },
        { provide: AchievementService, useValue: { list: () => of([]) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not persist a predefined avatar until the user confirms', () => {
    const avatarUrl = 'https://avatar.test/a.svg';

    component.selectAvatar(avatarUrl);

    expect(component.selectedPredefinedAvatar()).toBe(avatarUrl);
    expect(userService.updateAvatarUrl).not.toHaveBeenCalled();

    component.confirmSelectedAvatar();

    expect(userService.updateAvatarUrl).toHaveBeenCalledWith(avatarUrl);
  });

  it('should clear pending predefined avatar when cancelled', () => {
    component.selectAvatar('https://avatar.test/a.svg');

    component.cancelSelectedAvatar();

    expect(component.selectedPredefinedAvatar()).toBeNull();
    expect(userService.updateAvatarUrl).not.toHaveBeenCalled();
  });

  it('should persist audio preferences in localStorage', () => {
    component.audioForm.patchValue({ sfx: 25, bgm: 60, muted: true, reducedFeedback: false });

    expect(JSON.parse(localStorage.getItem('vs.audioPrefs') ?? '{}')).toEqual({
      sfx: 25,
      bgm: 60,
      muted: true,
      reducedFeedback: false,
    });
  });
});
