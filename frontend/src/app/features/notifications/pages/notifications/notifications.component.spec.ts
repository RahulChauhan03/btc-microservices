import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { NotificationService } from '../../../../core/services/notification.service';
import { ToastService } from '../../../../core/services/toast.service';
import { NotificationsComponent } from './notifications.component';

describe('NotificationsComponent', () => {
  const item = (id: number, read: boolean) => ({
    id, type: 'CLAIM_APPROVED', title: `Claim approved ${id}`, message: 'Your claim was approved.', link: '/claims',
    createdAt: '2026-10-01T10:00:00', read,
  });
  const service = {
    unread: signal(1),
    list: vi.fn(),
    refreshUnreadCount: vi.fn(() => of(1)),
    markRead: vi.fn(() => of(undefined)),
    markAllRead: vi.fn(() => of(undefined)),
  };

  function render() {
    vi.clearAllMocks();
    service.list.mockReturnValue(of({ items: [item(1, false), item(2, true)], total: 3 }));
    TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [provideRouter([]), { provide: NotificationService, useValue: service },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } }],
    });
    const fixture = TestBed.createComponent(NotificationsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists notifications, marks unread ones, and offers more pages', () => {
    const el: HTMLElement = render().nativeElement;
    expect(el.textContent).toContain('Claim approved 1');
    expect(el.querySelectorAll('li.unread').length).toBe(1);
    expect(el.textContent).toContain('Load more');
  });

  it('marks a notification read and opens its link', () => {
    const fixture = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.nativeElement.querySelector('li.unread button').click();
    fixture.detectChanges();

    expect(service.markRead).toHaveBeenCalledWith(1);
    expect(navigate).toHaveBeenCalledWith('/claims');
    expect(fixture.nativeElement.querySelectorAll('li.unread').length).toBe(0);
  });

  it('filters to unread and can mark everything read', () => {
    const fixture = render();
    fixture.componentInstance.setFilter(true);
    expect(service.list).toHaveBeenLastCalledWith(expect.objectContaining({ unreadOnly: true, page: 0 }));

    fixture.componentInstance.markAllRead();
    expect(service.markAllRead).toHaveBeenCalled();
  });

  it('shows an error with retry when loading fails', () => {
    const fixture = render();
    service.list.mockReturnValueOnce(throwError(() => new Error('down')));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('couldn’t load');
  });
});
