import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AuthService } from '../../../../core/services/auth.service';
import { UserService } from '../../../../core/services/user.service';
import { UserMe } from '../../../../core/models/auth.models';
import { AudioSettings, audioService } from '../../../../core/services/AudioService';
import {
  DEFAULT_NOTIFICATION_PREFS,
  NOTIFICATION_PREFS_KEY,
} from '../../../../core/models/notification.models';

type AudioFormValue = { sfxEnabled: boolean; bgmEnabled: boolean; sfxVolume: number; bgmVolume: number };

const DEFAULT_AUDIO_FORM: AudioFormValue = {
  sfxEnabled: true,
  bgmEnabled: true,
  sfxVolume: 80,
  bgmVolume: 40,
};
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TopbarComponent],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);
  private readonly router = inject(Router);

  readonly me = signal<UserMe | null>(null);
  readonly status = signal('');
  readonly error = signal('');
  readonly selectedPredefinedAvatar = signal<string | null>(null);
  readonly uploadedPreview = signal<string | null>(null);
  readonly uploadFile = signal<File | null>(null);
  readonly deleting = signal(false);

  readonly accountForm = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(64)]],
    email: ['', [Validators.required, Validators.email]],
  });

  readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  readonly cropForm = this.fb.nonNullable.group({
    zoom: [1, [Validators.min(1), Validators.max(2)]],
    x: [0, [Validators.min(-50), Validators.max(50)]],
    y: [0, [Validators.min(-50), Validators.max(50)]],
  });

  readonly notificationsForm = this.fb.nonNullable.group(DEFAULT_NOTIFICATION_PREFS);
  readonly audioForm = this.fb.nonNullable.group(DEFAULT_AUDIO_FORM);
  readonly deleteForm = this.fb.nonNullable.group({ username: ['', Validators.required] });

  readonly topbarUser = computed(() => ({
    name: this.me()?.username ?? this.auth.user()?.username ?? 'Jugador',
    xp: 0,
    avatarUrl: this.me()?.avatarUrl ?? this.auth.user()?.avatarUrl,
  }));

  readonly initials = computed(() => this.topbarUser().name.slice(0, 2).toUpperCase());

  readonly predefinedAvatars = [
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusRed&backgroundColor=e63946',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusGold&backgroundColor=f4c542',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusBlue&backgroundColor=4361ee',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusGreen&backgroundColor=2ec4b6',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusPurple&backgroundColor=7b2d8b',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusDark&backgroundColor=1e1e24',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusNeon&backgroundColor=2a2a32',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusVolt&backgroundColor=f4c542',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusByte&backgroundColor=4361ee',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusNova&backgroundColor=2ec4b6',
    'https://api.dicebear.com/9.x/bottts-neutral/svg?seed=VersusRift&backgroundColor=7b2d8b',
  ];

  ngOnInit(): void {
    this.users.me().subscribe({
      next: (u) => {
        this.me.set(u);
        this.accountForm.patchValue({ username: u.username, email: u.email });
      },
      error: () => this.error.set('No se pudo cargar tu cuenta.'),
    });

    this.notificationsForm.patchValue(this.readPrefs(NOTIFICATION_PREFS_KEY, DEFAULT_NOTIFICATION_PREFS));
    this.audioForm.patchValue(this.toAudioForm(audioService.getSettings()), { emitEvent: false });

    this.notificationsForm.valueChanges.subscribe((value) =>
      localStorage.setItem(NOTIFICATION_PREFS_KEY, JSON.stringify(value))
    );
    this.audioForm.valueChanges.subscribe(() => this.applyAudioSettings());
  }

  saveAccount(): void {
    if (this.accountForm.invalid) return;
    const value = this.accountForm.getRawValue();
    this.users.updateMe({ username: value.username }).subscribe({
      next: (u) => this.applyUser(u, 'Cuenta actualizada.'),
      error: () => this.error.set('No se pudo actualizar la cuenta.'),
    });
  }

  savePassword(): void {
    if (this.passwordForm.invalid) return;
    const value = this.passwordForm.getRawValue();
    if (value.newPassword !== value.confirmPassword) {
      this.error.set('La confirmacion no coincide.');
      return;
    }
    this.users.changePassword({
      currentPassword: value.currentPassword,
      newPassword: value.newPassword,
    }).subscribe({
      next: () => {
        this.passwordForm.reset();
        this.status.set('Contrasena actualizada.');
        this.error.set('');
      },
      error: () => this.error.set('Revisa tu contrasena actual.'),
    });
  }

  selectAvatar(avatarUrl: string): void {
    this.selectedPredefinedAvatar.set(avatarUrl);
    this.uploadedPreview.set(null);
    this.uploadFile.set(null);
    this.status.set('');
    this.error.set('');
  }

  confirmSelectedAvatar(): void {
    const avatarUrl = this.selectedPredefinedAvatar();
    if (!avatarUrl) return;
    this.users.updateAvatarUrl(avatarUrl).subscribe({
      next: (u) => {
        this.selectedPredefinedAvatar.set(null);
        this.applyUser(u, 'Avatar actualizado.');
      },
      error: () => this.error.set('No se pudo actualizar el avatar.'),
    });
  }

  cancelSelectedAvatar(): void {
    this.selectedPredefinedAvatar.set(null);
  }

  onAvatarFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      this.error.set('El avatar no puede superar 2MB.');
      return;
    }
    this.selectedPredefinedAvatar.set(null);
    this.uploadFile.set(file);
    const reader = new FileReader();
    reader.onload = () => this.uploadedPreview.set(String(reader.result));
    reader.readAsDataURL(file);
  }

  async uploadCroppedAvatar(): Promise<void> {
    const file = this.uploadFile();
    const preview = this.uploadedPreview();
    if (!file || !preview) return;
    const blob = await this.cropToBlob(preview);
    this.users.uploadAvatar(blob).subscribe({
      next: (u) => this.applyUser(u, 'Avatar actualizado.'),
      error: () => this.error.set('No se pudo subir el avatar.'),
    });
  }

  deleteAccount(): void {
    const username = this.me()?.username;
    if (!username || this.deleteForm.getRawValue().username !== username) {
      this.error.set('Escribe tu username exacto para confirmar.');
      return;
    }
    this.deleting.set(true);
    this.users.deleteMe().subscribe({
      next: () => {
        this.auth.clear();
        this.router.navigateByUrl('/landing');
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('No se pudo eliminar la cuenta.');
      },
    });
  }

  previewSfx(): void {
    audioService.play('correct');
  }

  previewBgm(): void {
    audioService.playBgm('bgm_menu');
  }

  private applyUser(u: UserMe, message: string): void {
    this.me.set(u);
    this.auth.updateCachedUser({ username: u.username, avatarUrl: u.avatarUrl, role: u.role });
    this.accountForm.patchValue({ username: u.username, email: u.email });
    this.status.set(message);
    this.error.set('');
  }

  private readPrefs<T>(key: string, fallback: T): T {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    try {
      return { ...fallback, ...JSON.parse(raw) };
    } catch {
      return fallback;
    }
  }

  private applyAudioSettings(): void {
    const value = this.audioForm.getRawValue();
    audioService.setSfxEnabled(value.sfxEnabled);
    audioService.setBgmEnabled(value.bgmEnabled);
    audioService.setVolume(value.sfxVolume / 100, value.bgmVolume / 100);
  }

  private toAudioForm(settings: AudioSettings): AudioFormValue {
    return {
      sfxEnabled: settings.sfxEnabled,
      bgmEnabled: settings.bgmEnabled,
      sfxVolume: this.toPercent(settings.sfxVolume),
      bgmVolume: this.toPercent(settings.bgmVolume),
    };
  }

  private toPercent(value: number): number {
    if (!Number.isFinite(value)) return 0;
    return Math.round(Math.max(0, Math.min(1, value)) * 100);
  }

  private cropToBlob(src: string): Promise<Blob> {
    const img = new Image();
    img.src = src;
    const crop = this.cropForm.getRawValue();
    return new Promise((resolve, reject) => {
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = 256;
        canvas.height = 256;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          reject(new Error('Canvas unavailable'));
          return;
        }
        const scale = crop.zoom;
        const size = Math.min(img.width, img.height) / scale;
        const sx = (img.width - size) / 2 - (crop.x / 100) * size;
        const sy = (img.height - size) / 2 - (crop.y / 100) * size;
        ctx.drawImage(img, sx, sy, size, size, 0, 0, 256, 256);
        canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('Crop failed')), 'image/png', 0.92);
      };
      img.onerror = reject;
    });
  }
}
