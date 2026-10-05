import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'btc-flow-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  readonly mode = signal<ThemeMode>(this.resolveInitialMode());
  readonly isDark = computed(() => this.mode() === 'dark');

  constructor() {
    this.applyMode(this.mode(), false);
  }

  toggle(): void {
    this.setMode(this.isDark() ? 'light' : 'dark');
  }

  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      this.document.defaultView?.localStorage.setItem(THEME_STORAGE_KEY, mode);
    } catch {
      // Storage can be unavailable in private browsing; keep the in-memory choice.
    }
    this.applyMode(mode, true);
  }

  private resolveInitialMode(): ThemeMode {
    try {
      const stored = this.document.defaultView?.localStorage.getItem(THEME_STORAGE_KEY);
      if (stored === 'light' || stored === 'dark') {
        return stored;
      }
    } catch {
      // Continue with the pre-bootstrap theme or system preference.
    }

    const initial = this.document.documentElement.dataset['theme'];
    if (initial === 'light' || initial === 'dark') {
      return initial;
    }

    return this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private applyMode(mode: ThemeMode, animate: boolean): void {
    const root = this.document.documentElement;
    const view = this.document.defaultView;
    const reducedMotion = view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    if (animate && !reducedMotion) {
      root.classList.add('theme-transition');
      view?.setTimeout(() => root.classList.remove('theme-transition'), 220);
    } else {
      root.classList.remove('theme-transition');
    }

    root.dataset['theme'] = mode;
    root.style.colorScheme = mode;
    this.document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')?.setAttribute(
      'content',
      mode === 'dark' ? '#0f1117' : '#0b1f3a',
    );
  }
}