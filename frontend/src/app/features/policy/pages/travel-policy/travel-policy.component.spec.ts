import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { AuthUser } from '../../../../core/models/auth.models';
import { AuthService } from '../../../../core/services/auth.service';
import { PolicyService } from '../../../../core/services/policy.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TravelPolicyComponent } from './travel-policy.component';

describe('TravelPolicyComponent', () => {
  const policy = { id: 1, name: 'Standard', currency: 'USD', tripLimit: 1000, effectiveFrom: '2026-01-01', effectiveTo: null,
    categoryLimits: { MEAL: 50 }, updatedAt: '2026-01-01T00:00:00', active: true };
  const service = { list: vi.fn(), create: vi.fn(), update: vi.fn() };

  function render(role: AuthUser['role']) {
    vi.clearAllMocks();
    service.list.mockReturnValue(of([policy]));
    TestBed.configureTestingModule({
      imports: [TravelPolicyComponent],
      providers: [
        { provide: PolicyService, useValue: service },
        { provide: AuthService, useValue: { currentUser: signal({ id: 1, name: 'A', email: 'a@x.test', role }) } },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(TravelPolicyComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows employees the policy in effect, read-only', () => {
    const el: HTMLElement = render('EMPLOYEE').nativeElement;
    expect(el.textContent).toContain('Standard');
    expect(el.textContent).toContain('$1,000.00');
    expect(el.textContent).toContain('$50.00');
    expect(el.textContent).toContain('No limit');
    expect(Array.from(el.querySelectorAll('button')).some((b) => /New policy|Edit/.test(b.textContent ?? ''))).toBe(false);
  });

  it('shows a failed request as an error with retry, never as “no policy”', () => {
    const fixture = render('EMPLOYEE');
    service.list.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    fixture.componentInstance.load();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[role=alert]')?.textContent).toContain('expense service is unavailable');
    expect(el.textContent).not.toContain('No travel policy is in effect');

    service.list.mockReturnValueOnce(of([]));
    (el.querySelector('[role=alert] button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.textContent).toContain('No travel policy is in effect today');
  });

  it('lets administrators create a policy with validated amounts and shows server errors', () => {
    const fixture = render('ADMIN');
    const component = fixture.componentInstance;
    component.startNew();
    component.form.patchValue({ name: 'Next', effectiveFrom: '2027-01-01', effectiveTo: '2026-01-01', tripLimit: '1.005' });
    component.save();
    expect(service.create).not.toHaveBeenCalled();

    component.form.patchValue({ effectiveTo: '', tripLimit: '500' });
    component.form.controls.limits.patchValue({ HOTEL: '200' });
    service.create.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Another travel policy already covers part of this period' } })));
    component.save();
    fixture.detectChanges();
    expect(service.create).toHaveBeenCalledWith({ name: 'Next', currency: 'USD', effectiveFrom: '2027-01-01', effectiveTo: null,
      tripLimit: 500, categoryLimits: { HOTEL: 200 } });
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('already covers');
  });
});
