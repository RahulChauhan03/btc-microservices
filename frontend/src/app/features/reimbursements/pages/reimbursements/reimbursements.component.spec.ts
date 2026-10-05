import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthUser } from '../../../../core/models/auth.models';
import { Reimbursement, ReimbursementStatus } from '../../../../core/models/domain.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ReimbursementService } from '../../../../core/services/reimbursement.service';
import { ToastService } from '../../../../core/services/toast.service';
import { ReimbursementsComponent } from './reimbursements.component';

describe('ReimbursementsComponent', () => {
  const row = (id: number, ownerId: number, status: ReimbursementStatus = 'PENDING',
               next: ReimbursementStatus[] = ['PROCESSING', 'PAID']): Reimbursement => ({
    id, claimId: id, claimNumber: `C-${id}`, claimTitle: 'Trip', ownerId, status, amount: 42.5,
    paymentDate: null, paymentReference: null, updatedAt: '2026-10-01T10:00:00', allowedNext: next,
  });
  const service = { list: vi.fn(), update: vi.fn() };

  function render(me: AuthUser) {
    vi.clearAllMocks();
    service.list.mockReturnValue(of({ items: [row(1, 10), row(2, 1), row(3, 10, 'PAID', [])], total: 3 }));
    TestBed.configureTestingModule({
      imports: [ReimbursementsComponent],
      providers: [
        { provide: ReimbursementService, useValue: service },
        { provide: AuthService, useValue: { currentUser: signal(me) } },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(ReimbursementsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows employees their reimbursements read-only and says no money is moved', () => {
    const el: HTMLElement = render({ id: 10, name: 'E', email: 'e@x.test', role: 'EMPLOYEE' }).nativeElement;
    expect(el.textContent).toContain('C-1');
    expect(el.textContent).toContain('$42.50');
    expect(el.textContent).toContain('does not transfer money');
    expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Update'))).toBe(false);
  });

  it('shows why the list could not be loaded', () => {
    const fixture = render({ id: 10, name: 'E', email: 'e@x.test', role: 'EMPLOYEE' });
    service.list.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    fixture.componentInstance.load(0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('claims service is unavailable');
  });

  it('offers administrators updates only for others’ unfinished reimbursements', () => {
    const el: HTMLElement = render({ id: 1, name: 'A', email: 'a@x.test', role: 'ADMIN' }).nativeElement;
    const updates = el.querySelectorAll('button[aria-label^="Update reimbursement"]');
    expect(updates.length).toBe(1);
    expect(updates[0].getAttribute('aria-label')).toBe('Update reimbursement C-1');
  });

  it('requires a date and reference to record a payment, then saves through the API', () => {
    const fixture = render({ id: 1, name: 'A', email: 'a@x.test', role: 'ADMIN' });
    const component = fixture.componentInstance;
    component.startUpdate(row(1, 10));
    component.form.patchValue({ status: 'PAID', paymentReference: '' });
    component.save();
    expect(component.formError()).toContain('payment date and a payment reference');
    expect(service.update).not.toHaveBeenCalled();

    service.update.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Payment reference REF-1 is already recorded' } })));
    component.form.patchValue({ paymentReference: 'REF-1' });
    component.save();
    expect(component.formError()).toContain('already recorded');

    service.update.mockReturnValueOnce(of({ ...row(1, 10, 'PAID', []), paymentReference: 'REF-2' }));
    component.form.patchValue({ paymentReference: 'REF-2' });
    component.save();
    expect(service.update).toHaveBeenLastCalledWith(1, { status: 'PAID', paymentDate: component.today, paymentReference: 'REF-2' });
    expect(component.editing()).toBeNull();
  });
});
