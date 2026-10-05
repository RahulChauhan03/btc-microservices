import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface BarItem {
  label: string;
  value: number;
  /** Formatted value shown next to the bar (e.g. currency). */
  display: string;
  hint?: string;
}

/**
 * Horizontal bars for small, real data sets (no chart library). Each row reads as plain text
 * ("Meals, $120.00, 4 expenses") for assistive technology; the bar itself is decorative.
 */
@Component({
  selector: 'app-bar-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length) {
      <ul class="bar-list" [attr.aria-label]="label()">
        @for (item of items(); track item.label) {
          <li>
            <div class="bar-row">
              <span class="bar-label">{{ item.label }}</span>
              <span class="bar-value">{{ item.display }}</span>
            </div>
            <div class="bar-track" aria-hidden="true">
              <span class="bar-fill" [style.width.%]="percent(item.value)"></span>
            </div>
            @if (item.hint) {
              <small class="bar-hint">{{ item.hint }}</small>
            }
          </li>
        }
      </ul>
    } @else {
      <p class="bar-empty">{{ emptyText() }}</p>
    }
  `,
  styles: `
    .bar-list { display: grid; gap: 0.75rem; margin: 0; padding: 0; list-style: none; }
    .bar-row { display: flex; justify-content: space-between; gap: 1rem; font-size: 0.875rem; }
    .bar-label { color: var(--text); font-weight: 500; }
    .bar-value { font-weight: 600; font-variant-numeric: tabular-nums; }
    .bar-track { height: 8px; margin-top: 0.375rem; border-radius: 999px; background: var(--surface-sunken); overflow: hidden; }
    .bar-fill { display: block; height: 100%; min-width: 2px; border-radius: inherit; background: var(--brand); }
    .bar-hint { color: var(--text-subtle); font-size: 0.75rem; }
    .bar-empty { color: var(--text-muted); font-size: 0.875rem; padding: 1rem 0; }
  `,
})
export class BarListComponent {
  readonly items = input.required<BarItem[]>();
  readonly label = input.required<string>();
  readonly emptyText = input('No data for this period.');

  private readonly max = computed(() => Math.max(0, ...this.items().map((item) => item.value)));

  percent(value: number): number {
    const max = this.max();
    return max > 0 ? Math.max(0, (value / max) * 100) : 0;
  }
}
