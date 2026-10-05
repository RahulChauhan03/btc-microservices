import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ProfileService } from '../../core/services/profile.service';
import { ToastService } from '../../core/services/toast.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  const user = { id: 3, name: 'Erin', email: 'erin@x.test', phone: '1', role: 'EMPLOYEE' as const, createdAt: '2026-01-01T00:00:00' };
  const service = { me: vi.fn(), update: vi.fn(), changePassword: vi.fn() };
  const toast = { success: vi.fn(), error: vi.fn() };

  function render() {
    vi.clearAllMocks();
    service.me.mockReturnValue(of(user));
    TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [{ provide: ProfileService, useValue: service }, { provide: ToastService, useValue: toast }],
    });
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows email and role read-only and saves only name and phone', () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('erin@x.test');
    expect(el.textContent).toContain('Employee');
    expect(el.querySelector('input[formcontrolname="email"]')).toBeNull();

    service.update.mockReturnValue(of({ ...user, name: 'Erin Q' }));
    fixture.componentInstance.profileForm.setValue({ name: ' Erin Q ', phone: '555' });
    fixture.componentInstance.profileForm.markAsDirty();
    fixture.componentInstance.saveProfile();
    expect(service.update).toHaveBeenCalledWith('Erin Q', '555');
    expect(toast.success).toHaveBeenCalled();
  });

  it('validates the new password and shows a wrong current password inline', () => {
    const fixture = render();
    const form = fixture.componentInstance.passwordForm;
    form.setValue({ currentPassword: 'x', newPassword: 'short', confirmPassword: 'short' });
    fixture.componentInstance.changePassword();
    expect(service.changePassword).not.toHaveBeenCalled();

    form.setValue({ currentPassword: 'wrong-pass', newPassword: 'brand-new-pass', confirmPassword: 'brand-new-pass' });
    service.changePassword.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Current password is incorrect' } })));
    fixture.componentInstance.changePassword();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('Current password is incorrect');

    service.changePassword.mockReturnValueOnce(of(undefined));
    fixture.componentInstance.changePassword();
    expect(service.changePassword).toHaveBeenLastCalledWith('wrong-pass', 'brand-new-pass', 'brand-new-pass');
    expect(form.getRawValue()).toEqual({ currentPassword: '', newPassword: '', confirmPassword: '' });
  });
});
