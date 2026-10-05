import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthUser } from '../core/models/auth.models';
import { AuthService } from '../core/services/auth.service';
import { NotificationService } from '../core/services/notification.service';
import { THEME_STORAGE_KEY, ThemeService } from '../core/services/theme.service';
import { AppShellComponent } from './app-shell.component';

describe('AppShellComponent navigation', () => {
  afterEach(() => localStorage.removeItem(THEME_STORAGE_KEY));

  function render(role: AuthUser['role']) {
    const user = signal<AuthUser>({ id: 1, name: 'Asha Rao', email: 'asha@example.com', role });
    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: user, logout: vi.fn() } },
        { provide: NotificationService, useValue: { unread: signal(3), refreshUnreadCount: () => of(3) } },
      ],
    });
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    return fixture;
  }

  const links = (el: HTMLElement) => Array.from(el.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));

  it('shows user management to administrators', () => {
    expect(links(render('ADMIN').nativeElement)).toEqual(['/dashboard', '/trips', '/expenses', '/claims', '/reimbursements', '/policy', '/employees', '/reports', '/users', '/audit']);
  });

  it('shows the unread count on an accessible notification bell', () => {
    const bell = render('EMPLOYEE').nativeElement.querySelector('a[href="/notifications"]');
    expect(bell?.getAttribute('aria-label')).toBe('Notifications, 3 unread');
    expect(bell?.textContent).toContain('3');
  });

  it('hides user management from employees', () => {
    const el = render('EMPLOYEE').nativeElement as HTMLElement;
    expect(links(el)).toEqual(['/dashboard', '/trips', '/expenses', '/claims', '/reimbursements', '/policy']);
    expect(el.textContent).toContain('AR');
  });

  it('toggles and persists the theme from the profile menu', async () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    const fixture = render('EMPLOYEE');
    const theme = TestBed.inject(ThemeService);
    const initialMode = theme.mode();

    fixture.nativeElement.querySelector('button[aria-label="Account menu"]').click();
    fixture.detectChanges();
    await fixture.whenStable();

    const toggle = document.body.querySelector<HTMLButtonElement>('button[aria-label="Toggle dark mode"]');
    expect(toggle).not.toBeNull();
    expect(toggle?.getAttribute('aria-pressed')).toBe(String(initialMode === 'dark'));

    toggle?.click();
    fixture.detectChanges();

    expect(theme.mode()).toBe(initialMode === 'dark' ? 'light' : 'dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe(theme.mode());
    expect(document.documentElement.dataset['theme']).toBe(theme.mode());
    localStorage.removeItem(THEME_STORAGE_KEY);
  });
});
