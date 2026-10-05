import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';

export interface LoaderTiming {
  /** Requests finishing faster than this never show the loader (no flicker). */
  showDelayMs: number;
  /** Once shown, the loader stays at least this long so it does not blink. */
  minVisibleMs: number;
}

export const LOADER_TIMING = new InjectionToken<LoaderTiming>('LOADER_TIMING', {
  providedIn: 'root',
  factory: () => ({ showDelayMs: 250, minVisibleMs: 400 }),
});

export const DEFAULT_LOADER_MESSAGE = 'Preparing your workspace…';

/**
 * Global loading state shared by every pending request. A counter keeps the loader up until the last
 * concurrent request has finished; the loading interceptor guarantees stop() on success, error and cancel.
 */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly timing = inject(LOADER_TIMING);
  private readonly activeRequests = signal(0);
  private readonly visibleSignal = signal(false);
  private readonly messageSignal = signal(DEFAULT_LOADER_MESSAGE);
  private showTimer: ReturnType<typeof setTimeout> | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;
  private shownAt = 0;

  readonly isLoading = computed(() => this.activeRequests() > 0);
  /** Whether the global loader should be on screen (debounced view of isLoading). */
  readonly visible = this.visibleSignal.asReadonly();
  readonly message = this.messageSignal.asReadonly();

  start(message?: string | null): void {
    if (message) {
      this.messageSignal.set(message);
    }
    this.activeRequests.update((count) => count + 1);
    this.clearTimer('hide');
    if (!this.visibleSignal() && !this.showTimer) {
      this.showTimer = setTimeout(() => {
        this.showTimer = null;
        this.shownAt = Date.now();
        this.visibleSignal.set(true);
      }, this.timing.showDelayMs);
    }
  }

  stop(): void {
    this.activeRequests.update((count) => Math.max(0, count - 1));
    if (this.activeRequests() > 0) {
      return;
    }
    this.clearTimer('show');
    if (!this.visibleSignal()) {
      this.messageSignal.set(DEFAULT_LOADER_MESSAGE);
      return;
    }
    const remaining = Math.max(0, this.timing.minVisibleMs - (Date.now() - this.shownAt));
    this.hideTimer = setTimeout(() => {
      this.hideTimer = null;
      this.visibleSignal.set(false);
      this.messageSignal.set(DEFAULT_LOADER_MESSAGE);
    }, remaining);
  }

  private clearTimer(which: 'show' | 'hide'): void {
    const timer = which === 'show' ? this.showTimer : this.hideTimer;
    if (timer) {
      clearTimeout(timer);
    }
    if (which === 'show') {
      this.showTimer = null;
    } else {
      this.hideTimer = null;
    }
  }
}
