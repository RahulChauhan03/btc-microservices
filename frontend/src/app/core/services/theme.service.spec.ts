import { TestBed } from '@angular/core/testing';

import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.classList.remove('theme-transition');
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.classList.remove('theme-transition');
  });

  it('restores a saved preference and applies it to the document', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    document.documentElement.dataset['theme'] = 'light';

    const theme = TestBed.inject(ThemeService);

    expect(theme.mode()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('uses the theme applied before Angular bootstraps when no preference is saved', () => {
    document.documentElement.dataset['theme'] = 'dark';

    const theme = TestBed.inject(ThemeService);

    expect(theme.mode()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('toggles and persists only the theme preference', () => {
    const theme = TestBed.inject(ThemeService);
    theme.setMode('light');

    theme.toggle();
    expect(theme.mode()).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');

    theme.toggle();
    expect(theme.mode()).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });
});