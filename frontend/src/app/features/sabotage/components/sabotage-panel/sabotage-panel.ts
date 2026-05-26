import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { SabotageType } from '../../../../core/models/duel.models';

export interface SabotageOption {
  type: SabotageType;
  label: string;
  description: string;
}

const CATALOG: SabotageOption[] = [
  { type: 'TIME_BOMB', label: 'BOMBA DE TIEMPO', description: 'Reduce 5s al timer del rival' },
  { type: 'OBFUSCATION', label: 'OFUSCACIÓN', description: 'Oculta una opción al rival' },
  { type: 'LIFE_STEAL', label: 'ROBO DE VIDA', description: 'Si el rival falla, recuperas 1 vida' },
];

@Component({
  selector: 'app-sabotage-panel',
  standalone: true,
  templateUrl: './sabotage-panel.html',
  styleUrl: './sabotage-panel.scss',
})
export class SabotagePanelComponent {
  @Input() tokens = 0;
  @Input() canActivate = true;
  @Output() activate = new EventEmitter<SabotageType>();

  readonly catalog = signal(CATALOG);

  readonly disabled = computed(() => !this.canActivate || this.tokens <= 0);

  isLocked(): boolean {
    return this.tokens <= 0 || !this.canActivate;
  }

  click(type: SabotageType): void {
    if (this.isLocked()) return;
    this.activate.emit(type);
  }
}
