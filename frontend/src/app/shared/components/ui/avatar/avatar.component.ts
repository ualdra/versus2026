import { Component, computed, input, signal } from '@angular/core';

export type AvatarSize = 'sm' | 'md' | 'lg' | 'xl';

@Component({
  selector: 'vs-avatar',
  standalone: true,
  imports: [],
  templateUrl: './avatar.component.html',
  styleUrl: './avatar.component.scss',
})
export class AvatarComponent {
  readonly name = input<string>('Jugador');
  readonly avatarUrl = input<string | null | undefined>(null);
  readonly alt = input<string>('');
  readonly size = input<AvatarSize>('md');
  readonly sizePx = input<number | null>(null);
  readonly fontSizePx = input<number | null>(null);

  private readonly failedUrl = signal<string | null>(null);

  readonly imageUrl = computed(() => {
    const url = this.avatarUrl();
    if (!url || this.failedUrl() === url) return null;
    return url;
  });

  readonly initials = computed(() => {
    const clean = this.name().trim();
    if (!clean) return '??';
    const parts = clean.split(/\s+/);
    if (parts.length > 1) {
      return `${parts[0][0] ?? ''}${parts[1][0] ?? ''}`.toUpperCase();
    }
    return clean.slice(0, 2).toUpperCase();
  });

  markImageFailed(url: string): void {
    this.failedUrl.set(url);
  }
}
