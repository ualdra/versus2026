import { Component, Input } from '@angular/core';
import { SabotageType } from '../../../../core/models/duel.models';

@Component({
  selector: 'app-effect-indicator',
  standalone: true,
  templateUrl: './effect-indicator.html',
  styleUrl: './effect-indicator.scss',
})
export class EffectIndicatorComponent {
  @Input() type!: SabotageType;

  label(): string {
    switch (this.type) {
      case 'TIME_BOMB': return '⏱ BOMBA DE TIEMPO';
      case 'OBFUSCATION': return '🌫 OFUSCACIÓN';
      case 'LIFE_STEAL': return '🩸 ROBO DE VIDA';
    }
  }
}
