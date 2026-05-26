import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-elo-change-notice',
  standalone: true,
  templateUrl: './elo-change-notice.html',
  styleUrl: './elo-change-notice.scss',
})
export class EloChangeNoticeComponent {
  delta = input<number | null | undefined>(null);
  previousRating = input<number | null | undefined>(null);
  currentRating = input<number | null | undefined>(null);

  readonly visible = computed(() => this.delta() !== null && this.delta() !== undefined);
  readonly positive = computed(() => (this.delta() ?? 0) > 0);
  readonly negative = computed(() => (this.delta() ?? 0) < 0);
  readonly formattedDelta = computed(() => {
    const value = this.delta() ?? 0;
    return value > 0 ? `+${value}` : String(value);
  });
}
