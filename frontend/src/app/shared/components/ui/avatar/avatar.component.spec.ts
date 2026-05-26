import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AvatarComponent } from './avatar.component';

describe('AvatarComponent', () => {
  let fixture: ComponentFixture<AvatarComponent>;
  let component: AvatarComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AvatarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AvatarComponent);
    component = fixture.componentInstance;
  });

  it('renders a custom avatar image when avatarUrl is present', () => {
    fixture.componentRef.setInput('name', 'Ada Lovelace');
    fixture.componentRef.setInput('avatarUrl', 'data:image/png;base64,abc');
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('.vs-avatar img') as HTMLImageElement | null;
    expect(img?.getAttribute('src')).toBe('data:image/png;base64,abc');
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  it('falls back to initials when avatarUrl is missing', () => {
    fixture.componentRef.setInput('name', 'Ada Lovelace');
    fixture.componentRef.setInput('avatarUrl', null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.vs-avatar img')).toBeNull();
    expect(fixture.nativeElement.textContent.trim()).toBe('AL');
  });

  it('falls back to initials when the image fails to load', () => {
    fixture.componentRef.setInput('name', 'player');
    fixture.componentRef.setInput('avatarUrl', 'https://avatar.test/missing.png');
    fixture.detectChanges();

    component.markImageFailed('https://avatar.test/missing.png');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.vs-avatar img')).toBeNull();
    expect(fixture.nativeElement.textContent.trim()).toBe('PL');
  });
});
