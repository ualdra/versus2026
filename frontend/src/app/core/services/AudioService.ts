export type SoundKey =
  | 'correct'
  | 'wrong'
  | 'streak_3'
  | 'streak_5'
  | 'game_start'
  | 'game_over'
  | 'sabotage_sent'
  | 'sabotage_received'
  | 'tick'
  | 'ui_click';

export type BgmKey = 'bgm_menu' | 'bgm_game';

export interface AudioSettings {
  sfxEnabled: boolean;
  bgmEnabled: boolean;
  sfxVolume: number;
  bgmVolume: number;
}

type AudioKind = 'sfx' | 'bgm';
type AudioSource = { extension: 'ogg' | 'mp3' | 'wav'; type: string };

const AUDIO_SETTINGS_KEY = 'audio_settings';
const DEFAULT_AUDIO_SETTINGS: AudioSettings = {
  sfxEnabled: true,
  bgmEnabled: true,
  sfxVolume: 0.8,
  bgmVolume: 0.4,
};

export class AudioService {
  private readonly cache = new Map<SoundKey | BgmKey, HTMLAudioElement>();
  private settings: AudioSettings = this.readSettings();
  private activeBgm: HTMLAudioElement | null = null;
  private activeBgmKey: BgmKey | null = null;
  private desiredBgm: { track: BgmKey; loop: boolean } | null = null;
  private preferredSources: AudioSource[] | null = null;
  private unlockListenersArmed = false;

  play(sound: SoundKey): void {
    if (!this.settings.sfxEnabled || !this.canUseAudio()) return;

    const cached = this.getOrCreateAudio(sound, 'sfx');
    const instance = cached.cloneNode(true) as HTMLAudioElement;
    instance.volume = this.settings.sfxVolume;
    instance.loop = false;
    instance.addEventListener('error', this.ignoreMediaError, { once: true });
    this.resetCurrentTime(instance);
    this.safePlay(instance);
  }

  playBgm(track: BgmKey, loop = true): void {
    this.desiredBgm = { track, loop };
    if (!this.settings.bgmEnabled || !this.canUseAudio()) return;

    if (this.activeBgm && this.activeBgmKey === track) {
      this.activeBgm.volume = this.settings.bgmVolume;
      this.activeBgm.loop = loop;
      if (this.activeBgm.paused) {
        if (this.activeBgm.ended) {
          this.resetCurrentTime(this.activeBgm);
        }
        this.safePlay(this.activeBgm, true);
      }
      return;
    }

    this.stopActiveBgm();
    const audio = this.getOrCreateAudio(track, 'bgm');
    audio.volume = this.settings.bgmVolume;
    audio.loop = loop;
    this.resetCurrentTime(audio);
    this.activeBgm = audio;
    this.activeBgmKey = track;
    this.safePlay(audio, true);
  }

  resumeBgm(): void {
    if (!this.desiredBgm || !this.settings.bgmEnabled || !this.canUseAudio()) return;
    this.playBgm(this.desiredBgm.track, this.desiredBgm.loop);
  }

  stopBgm(): void {
    this.desiredBgm = null;
    this.stopActiveBgm();
  }

  private stopActiveBgm(): void {
    if (!this.activeBgm) return;

    this.activeBgm.pause();
    this.resetCurrentTime(this.activeBgm);
    this.activeBgm = null;
    this.activeBgmKey = null;
  }

  setVolume(sfx: number, bgm: number): void {
    this.settings = {
      ...this.settings,
      sfxVolume: this.clampVolume(sfx),
      bgmVolume: this.clampVolume(bgm),
    };

    if (this.activeBgm) {
      this.activeBgm.volume = this.settings.bgmVolume;
    }

    this.persistSettings();
  }

  setSfxEnabled(enabled: boolean): void {
    this.settings = { ...this.settings, sfxEnabled: enabled };
    this.persistSettings();
  }

  setBgmEnabled(enabled: boolean): void {
    this.settings = { ...this.settings, bgmEnabled: enabled };
    if (!enabled) {
      this.stopActiveBgm();
    } else if (this.desiredBgm) {
      this.playBgm(this.desiredBgm.track, this.desiredBgm.loop);
    }
    this.persistSettings();
  }

  getSettings(): AudioSettings {
    return { ...this.settings };
  }

  private getOrCreateAudio(key: SoundKey | BgmKey, kind: AudioKind): HTMLAudioElement {
    const cached = this.cache.get(key);
    if (cached) return cached;

    const audio = document.createElement('audio');
    audio.preload = 'auto';
    audio.addEventListener('error', this.ignoreMediaError);

    const basePath = `/audio/${kind}/${key}`;
    for (const source of this.getPreferredSources()) {
      const sourceElement = document.createElement('source');
      sourceElement.src = `${basePath}.${source.extension}`;
      sourceElement.type = source.type;
      audio.appendChild(sourceElement);
    }

    this.safeLoad(audio);
    this.cache.set(key, audio);
    return audio;
  }

  private getPreferredSources(): AudioSource[] {
    if (this.preferredSources) return this.preferredSources;

    const probe = document.createElement('audio');
    const wav: AudioSource = { extension: 'wav', type: 'audio/wav' };
    const ogg: AudioSource = { extension: 'ogg', type: 'audio/ogg' };
    const mp3: AudioSource = { extension: 'mp3', type: 'audio/mpeg' };

    const codecSources = probe.canPlayType('audio/ogg; codecs="vorbis"') ? [ogg, mp3] : [mp3, ogg];
    this.preferredSources = [wav, ...codecSources];
    return this.preferredSources;
  }

  private safeLoad(audio: HTMLAudioElement): void {
    try {
      audio.load();
    } catch {
      // Missing assets or unsupported codecs must not break game flow.
    }
  }

  private safePlay(audio: HTMLAudioElement, retryBgmOnInteraction = false): void {
    try {
      const result = audio.play();
      result.catch(() => {
        if (retryBgmOnInteraction) {
          this.armBgmUnlockRetry();
        }
      });
    } catch {
      if (retryBgmOnInteraction) {
        this.armBgmUnlockRetry();
      }
      // Autoplay policies and missing assets are intentionally ignored.
    }
  }

  private armBgmUnlockRetry(): void {
    if (this.unlockListenersArmed || !this.canUseAudio()) return;

    this.unlockListenersArmed = true;
    const retry = (): void => {
      this.unlockListenersArmed = false;
      document.removeEventListener('pointerdown', retry);
      document.removeEventListener('keydown', retry);
      document.removeEventListener('touchstart', retry);

      if (this.desiredBgm && this.settings.bgmEnabled) {
        this.playBgm(this.desiredBgm.track, this.desiredBgm.loop);
      }
    };

    document.addEventListener('pointerdown', retry, { once: true });
    document.addEventListener('keydown', retry, { once: true });
    document.addEventListener('touchstart', retry, { once: true });
  }

  private resetCurrentTime(audio: HTMLAudioElement): void {
    try {
      audio.currentTime = 0;
    } catch {
      // Some browsers reject currentTime changes before metadata is loaded.
    }
  }

  private readSettings(): AudioSettings {
    const raw = this.readStorage(AUDIO_SETTINGS_KEY);
    if (!raw) return { ...DEFAULT_AUDIO_SETTINGS };

    try {
      const parsed = JSON.parse(raw) as Partial<AudioSettings>;
      return this.normalizeSettings(parsed);
    } catch {
      return { ...DEFAULT_AUDIO_SETTINGS };
    }
  }

  private normalizeSettings(value: Partial<AudioSettings>): AudioSettings {
    return {
      sfxEnabled: typeof value.sfxEnabled === 'boolean' ? value.sfxEnabled : DEFAULT_AUDIO_SETTINGS.sfxEnabled,
      bgmEnabled: typeof value.bgmEnabled === 'boolean' ? value.bgmEnabled : DEFAULT_AUDIO_SETTINGS.bgmEnabled,
      sfxVolume: this.clampVolume(value.sfxVolume ?? DEFAULT_AUDIO_SETTINGS.sfxVolume),
      bgmVolume: this.clampVolume(value.bgmVolume ?? DEFAULT_AUDIO_SETTINGS.bgmVolume),
    };
  }

  private persistSettings(): void {
    try {
      localStorage.setItem(AUDIO_SETTINGS_KEY, JSON.stringify(this.settings));
    } catch {
      // Storage can be unavailable in private contexts or tests.
    }
  }

  private readStorage(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private clampVolume(value: number): number {
    if (!Number.isFinite(value)) return 0;
    return Math.max(0, Math.min(1, value));
  }

  private canUseAudio(): boolean {
    return typeof document !== 'undefined';
  }

  private readonly ignoreMediaError = (): void => undefined;
}

export const audioService = new AudioService();
