import { Component, input, output } from '@angular/core';
import { RollingNumberComponent } from '../rolling-number/rolling-number';

export type CardState = 'idle' | 'correct' | 'wrong';
export type CardPosition = 'left' | 'right';

export interface CardItem {
  label:  string;
  sub:    string;
  value:  number;
  unit:   string;
  cat:    string;
  subcat: string;
  stub:   string;
}

@Component({
  selector: 'app-compare-card',
  standalone: true,
  imports: [RollingNumberComponent],
  templateUrl: './compare-card.html',
  styleUrl:    './compare-card.scss',
})
export class CompareCardComponent {
  item     = input.required<CardItem>();
  state    = input<CardState>('idle');
  revealed = input(false);
  position = input<CardPosition>('left');

  picked = output<void>();

  get showValue(): boolean {
    return this.revealed() || this.state() === 'correct' || this.state() === 'wrong';
  }

  get classes(): string {
    return [
      'vs-compare',
      `vs-compare--${this.position()}`,
      this.state() === 'correct' ? 'vs-compare--correct' : '',
      this.state() === 'wrong'   ? 'vs-compare--wrong'   : '',
      this.state() === 'idle'    ? 'vs-compare--clickable': '',
    ].filter(Boolean).join(' ');
  }

  onClickCard(): void {
    if (this.state() === 'idle') this.picked.emit();
  }
}
