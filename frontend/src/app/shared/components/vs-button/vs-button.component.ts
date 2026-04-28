import { Component, HostBinding, input } from '@angular/core';

@Component({
  selector: 'app-vs-button',
  standalone: true,
  template: `
    <button
      [type]="type()"
      [class]="'btn btn--' + variant()"
      [style.width]="block() ? '100%' : null"
      [disabled]="disabled() || loading()"
    >
      @if (loading()) {
        <span class="vs-btn-spinner"></span>
      }
      <ng-content />
    </button>
  `,
  styles: [`
    :host { display: inline-block; }

    .vs-btn-spinner {
      width: 13px;
      height: 13px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #fff;
      border-radius: 50%;
      animation: vs-btn-spin 0.6s linear infinite;
    }

    @keyframes vs-btn-spin {
      to { transform: rotate(360deg); }
    }
  `],
})
export class VsButtonComponent {
  readonly variant = input<'primary' | 'danger' | 'ghost'>('primary');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly block = input(false);

  @HostBinding('style.display')
  get hostDisplay(): string {
    return this.block() ? 'block' : 'inline-block';
  }
}
