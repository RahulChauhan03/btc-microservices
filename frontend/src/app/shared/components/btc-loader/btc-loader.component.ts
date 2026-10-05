import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { BrandMarkComponent } from '../brand-mark/brand-mark.component';

/**
 * The single BTC Flow loader: a small aircraft dot orbiting the brand mark.
 * - overlay: the global, app-level loader (rendered once by AppComponent from LoadingService).
 * - inline: inside a card or section while that part of the page loads.
 * Pure CSS animation; static under prefers-reduced-motion. Announced politely to assistive technology.
 */
@Component({
  selector: 'app-btc-loader',
  imports: [BrandMarkComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'status',
    'aria-live': 'polite',
    '[class.overlay]': "mode() === 'overlay'",
    '[class.inline]': "mode() === 'inline'",
  },
  templateUrl: './btc-loader.component.html',
  styleUrl: './btc-loader.component.css',
})
export class BtcLoaderComponent {
  readonly mode = input<'overlay' | 'inline'>('inline');
  readonly message = input('Loading…');
}
