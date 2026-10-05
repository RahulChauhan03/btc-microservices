import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthUser } from '../core/models/auth.models';
import { AuthService } from '../core/services/auth.service';
import { NotificationService } from '../core/services/notification.service';
import { AppShellComponent } from './app-shell.component';

describe('AppShellComponent navigation', () => {
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
    return fixture.nativeElement as HTMLElement;
  }

  const links = (el: HTMLElement) => Array.from(el.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));

  it('shows user management to administrators', () => {
    expect(links(render('ADMIN'))).toEqual(['/dashboard', '/trips', '/expenses', '/claims', '/reimbursements', '/policy', '/employees', '/reports', '/users', '/audit']);
  });

  it('shows the unread count on an accessible notification bell', () => {
    const bell = render('EMPLOYEE').querySelector('a[href="/notifications"]');
    expect(bell?.getAttribute('aria-label')).toBe('Notifications, 3 unread');
    expect(bell?.textContent).toContain('3');
  });

  it('hides user management from employees', () => {
    const el = render('EMPLOYEE');
    expect(links(el)).toEqual(['/dashboard', '/trips', '/expenses', '/claims', '/reimbursements', '/policy']);
    expect(el.textContent).toContain('AR');
  });
});
