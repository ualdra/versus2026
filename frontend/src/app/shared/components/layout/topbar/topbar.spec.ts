import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { AuthUser } from '../../../../core/models/auth.models';
import { AuthService } from '../../../../core/services/auth.service';
import { TopbarComponent } from './topbar';

describe('TopbarComponent', () => {
  let fixture: ComponentFixture<TopbarComponent>;
  const currentUser = signal<AuthUser | null>(null);

  beforeEach(async () => {
    currentUser.set({
      id: 'user-1',
      username: 'playerReal',
      role: 'PLAYER',
      avatarUrl: null,
    });

    await TestBed.configureTestingModule({
      imports: [TopbarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user: currentUser.asReadonly() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TopbarComponent);
    fixture.detectChanges();
  });

  it('renders the authenticated username without mock data', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('playerReal');
    expect(compiled.textContent).not.toContain('aritzz92');
    expect(compiled.textContent).not.toContain('4280 XP');
  });

  it('does not render the XP line until a real XP source exists', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).not.toContain('XP');
  });

  it('renders initials when the authenticated user has no avatar URL', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.vs-avatar')?.textContent?.trim()).toBe('PL');
    expect(compiled.querySelector('img.vs-avatar')).toBeNull();
  });

  it('renders the user avatar image when avatarUrl exists', () => {
    currentUser.set({
      id: 'user-1',
      username: 'playerReal',
      role: 'PLAYER',
      avatarUrl: 'https://example.com/avatar.png',
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const img = compiled.querySelector('img.vs-avatar') as HTMLImageElement | null;

    expect(img).toBeTruthy();
    expect(img?.src).toBe('https://example.com/avatar.png');
    expect(img?.alt).toBe('playerReal');
  });
});
