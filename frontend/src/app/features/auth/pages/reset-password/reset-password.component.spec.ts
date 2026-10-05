import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { MessageResponse, PasswordResetService } from '../../../../core/services/password-reset.service';
import { ResetPasswordComponent } from './reset-password.component';

describe('ResetPasswordComponent', () => {
  const resetService = { resetPassword: vi.fn() };
  let navigate: ReturnType<typeof vi.spyOn>;

  function render(token: string | null) {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideRouter([]),
        { provide: PasswordResetService, useValue: resetService },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();
    return fixture;
  }

  function fill(fixture: ReturnType<typeof render>, password: string, confirm: string): void {
    const [first, second] = fixture.nativeElement.querySelectorAll('input');
    first.value = password;
    first.dispatchEvent(new Event('input'));
    first.dispatchEvent(new Event('blur'));
    second.value = confirm;
    second.dispatchEvent(new Event('input'));
    second.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function submit(fixture: ReturnType<typeof render>): void {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  const button = (fixture: ReturnType<typeof render>): HTMLButtonElement =>
    fixture.nativeElement.querySelector('button[type=submit]');

  it('removes the token from the address bar and history immediately', () => {
    render('secret-token');
    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {}, replaceUrl: true }));
  });

  it('shows an invalid-link state when there is no token', () => {
    const fixture = render(null);
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('invalid or has already been used');
    expect(fixture.nativeElement.querySelector('a[href="/forgot-password"]')).not.toBeNull();
  });

  it('keeps submit disabled until both passwords are valid and match', () => {
    const fixture = render('secret-token');
    expect(button(fixture).disabled).toBe(true);

    fill(fixture, 'short', 'short');
    expect(button(fixture).disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Use at least 8 characters.');

    fill(fixture, 'long-enough-1', 'long-enough-2');
    expect(button(fixture).disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Passwords don’t match.');

    fill(fixture, 'é'.repeat(40), 'é'.repeat(40));
    expect(button(fixture).disabled).toBe(true);

    fill(fixture, 'long-enough-1', 'long-enough-1');
    expect(button(fixture).disabled).toBe(false);
    expect(resetService.resetPassword).not.toHaveBeenCalled();
  });

  it('toggles each password field independently', () => {
    const fixture = render('secret-token');
    fixture.nativeElement.querySelector('button[aria-label="Show new password"]').click();
    fixture.detectChanges();
    const [first, second] = fixture.nativeElement.querySelectorAll('input');
    expect(first.type).toBe('text');
    expect(second.type).toBe('password');
  });

  it('shows the BTC loader while saving, then clears the form and goes to sign in', () => {
    const response = new Subject<MessageResponse>();
    resetService.resetPassword.mockReturnValue(response);
    const fixture = render('secret-token');
    fill(fixture, 'long-enough-1', 'long-enough-1');
    submit(fixture);

    expect(resetService.resetPassword).toHaveBeenCalledWith('secret-token', 'long-enough-1', 'long-enough-1');
    expect(fixture.nativeElement.querySelector('app-btc-loader')?.textContent).toContain('Updating your password');

    response.next({ message: 'ok' });
    response.complete();
    expect(fixture.componentInstance.form.getRawValue()).toEqual({ newPassword: '', confirmPassword: '' });
    expect(navigate).toHaveBeenLastCalledWith(['/login'], expect.objectContaining({ state: { passwordReset: true } }));
  });

  it.each([
    [404, 'invalid or has already been used'],
    [410, 'has expired'],
  ])('maps HTTP %i to a dedicated link state', (status, text) => {
    const fixture = render('used-or-old-token');
    resetService.resetPassword.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
    fill(fixture, 'long-enough-1', 'long-enough-1');
    submit(fixture);

    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain(text);
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('shows server-side password errors and lets the user retry', () => {
    const fixture = render('secret-token');
    resetService.resetPassword
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Passwords do not match' } })))
      .mockReturnValueOnce(of({ message: 'ok' }));
    fill(fixture, 'long-enough-1', 'long-enough-1');
    submit(fixture);
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('Passwords do not match');

    submit(fixture);
    expect(navigate).toHaveBeenLastCalledWith(['/login'], expect.anything());
  });
});
