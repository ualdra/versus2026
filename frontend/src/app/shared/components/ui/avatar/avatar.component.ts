import { Component, computed, input, signal } from '@angular/core';
import { environment } from '../../../../../environments/environment';

export type AvatarSize = 'sm' | 'md' | 'lg' | 'xl';

const ABSOLUTE_AVATAR_URL = /^(?:https?:|data:|blob:)/i;
const HOST_LIKE_AVATAR_URL = /^[a-z0-9.-]+\.[a-z]{2,}(?::\d+)?(?:[/?#]|$)/i;

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
    const url = this.normalizedAvatarUrl(this.avatarUrl());
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

  private normalizedAvatarUrl(value: string | null | undefined): string | null {
    const url = value?.trim();
    if (!url) return null;
    if (ABSOLUTE_AVATAR_URL.test(url)) return url;
    if (url.startsWith('//')) return `https:${url}`;
    if (url.startsWith('/media-files/')) return this.resolveFromApiOrigin(url);
    if (url.startsWith('/')) return url;
    if (HOST_LIKE_AVATAR_URL.test(url)) return `https://${url}`;
    return url;
  }

  private resolveFromApiOrigin(path: string): string {
    const fallbackOrigin = globalThis.location?.origin ?? 'http://localhost';
    try {
      return new URL(path, new URL(environment.apiBaseUrl, fallbackOrigin).origin).href;
    } catch {
      return path;
    }
  }
}
