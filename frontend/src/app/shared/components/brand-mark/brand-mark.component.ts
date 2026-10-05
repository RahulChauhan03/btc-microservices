import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * BTC Flow monogram: a route travelling from origin to destination inside a rounded tile.
 * Inline SVG (no image request); decorative unless a label is given.
 */
@Component({
  selector: 'app-brand-mark',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'brand-mark',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[attr.role]': "label() ? 'img' : null",
    '[attr.aria-label]': 'label()',
    '[attr.aria-hidden]': "label() ? null : 'true'",
  },
  template: `
    <svg viewBox="0 0 32 32" width="100%" height="100%" focusable="false">
      <rect width="32" height="32" rx="9" class="tile" />
      <path d="M9.5 22.5c2.5-8.5 10.5-4.5 13-13" class="route" />
      <circle cx="9.5" cy="22.5" r="2.6" class="origin" />
      <circle cx="22.5" cy="9.5" r="2.6" class="destination" />
    </svg>
  `,
  styles: `
    :host { display: inline-block; flex: none; line-height: 0; }
    .tile { fill: var(--brand-ink, #0b1f3a); }
    .route { fill: none; stroke: #fff; stroke-width: 2.4; stroke-linecap: round; stroke-dasharray: 2.2 3; }
    .origin { fill: var(--brand-accent, #2dd4bf); }
    .destination { fill: #fff; }
  `,
})
export class BrandMarkComponent {
  readonly size = input(32);
  readonly label = input<string | null>(null);
}
