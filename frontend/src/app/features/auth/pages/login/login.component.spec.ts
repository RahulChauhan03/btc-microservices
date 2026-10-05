import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  const auth = { login: vi.fn() };

  function render() {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: { success: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return fixture;
  }

  function type(fixture: ReturnType<typeof render>, selector: string, value: string): void {
    const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('renders branding, labelled fields, the sign-in button and no demo credentials', () => {
    const el: HTMLElement = render().nativeElement;

    expect(el.textContent).toContain('BTC Flow');
    expect(el.querySelector('input[type=email][autocomplete=username]')).not.toBeNull();
    expect(el.querySelector('input[autocomplete=current-password]')).not.toBeNull();
    expect(el.querySelector('button[type=submit]')?.textContent).toContain('Sign in');
    expect(el.textContent?.toLowerCase()).not.toContain('demo');
  });

  it('confirms a completed password reset passed through navigation state', () => {
    history.replaceState({ passwordReset: true }, '');
    const el: HTMLElement = render().nativeElement;
    history.replaceState(null, '');
    expect(el.querySelector('[role=status]')?.textContent).toContain('Your password has been reset');
  });

  it('links to the forgot-password screen', () => {
    const link: HTMLAnchorElement = render().nativeElement.querySelector('a[href="/forgot-password"]');
    expect(link?.textContent).toContain('Forgot password?');
  });

  it('shows field errors and does not call the API for an invalid form', () => {
    const fixture = render();
    type(fixture, 'input[type=email]', 'not-an-email');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const errors = fixture.nativeElement.textContent;
    expect(errors).toContain('Enter a valid email address');
    expect(errors).toContain('Enter your password.');
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('toggles password visibility with an accessible label', () => {
    const fixture = render();
    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Show password"]');
    toggle.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input[autocomplete=current-password]').type).toBe('text');
    expect(toggle.getAttribute('aria-label')).toBe('Hide password');
  });

  it('signs in with the existing auth flow and navigates to the dashboard', () => {
    const fixture = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login.mockImplementation((_credentials, _remember, onSuccess) => onSuccess());
    type(fixture, 'input[type=email]', 'emp@example.com');
    type(fixture, 'input[autocomplete=current-password]', 'long-enough-pw');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(auth.login).toHaveBeenCalledWith({ email: 'emp@example.com', password: 'long-enough-pw' }, true,
      expect.any(Function), expect.any(Function));
    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('shows an inline error for wrong credentials and re-enables the button', () => {
    const fixture = render();
    auth.login.mockImplementation((_c, _r, _ok, onError) =>
      onError(new HttpErrorResponse({ status: 401 })));
    type(fixture, 'input[type=email]', 'emp@example.com');
    type(fixture, 'input[autocomplete=current-password]', 'wrong-password');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('Incorrect email or password.');
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBe(false);
  });
});
