import { Component, inject } from '@angular/core';
import { AchievementToastService } from '../../../core/services/achievement-toast.service';

@Component({
  selector: 'app-achievement-toasts',
  standalone: true,
  templateUrl: './achievement-toasts.html',
  styleUrl: './achievement-toasts.scss',
})
export class AchievementToastsComponent {
  readonly toasts = inject(AchievementToastService);

  iconLabel(iconKey: string): string {
    const labels: Record<string, string> = {
      first: '1',
      win: 'W',
      streak5: '5',
      streak10: '10',
      streak20: '20',
      precision: 'P',
      sniper: 'P',
      target: 'P',
      survival: 'S',
      shield: 'S',
      perfect: 'S',
      duel: 'D',
      arena: 'D',
      sabotage: 'SB',
      friends: 'F',
      invite: 'I',
      collector: 'C',
    };
    return labels[iconKey] ?? 'OK';
  }
}
