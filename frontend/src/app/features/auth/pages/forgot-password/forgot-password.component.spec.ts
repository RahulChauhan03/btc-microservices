import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { MessageResponse, PasswordResetService } from '../../../../core/services/password-reset.service';
import { ForgotPasswordComponent } from './forgot-password.component';

describe('ForgotPasswordComponent', () => {
  const GENERIC = 'If an account exists for that email, you will receive password-reset instructions.';
  const resetService = { requestReset: vi.fn() };

  function render() {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [provideRouter([]), { provide: PasswordResetService, useValue: resetService }],
    });
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    return fixture;
  }

  function type(fixture: ReturnType<typeof render>, email: string): void {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type=email]');
    input.value = email;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  const button = (fixture: ReturnType<typeof render>): HTMLButtonElement =>
    fixture.nativeElement.querySelector('button[type=submit]');

  function submit(fixture: ReturnType<typeof render>): void {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('enables Send reset link only for a valid email and explains invalid input', () => {
    const fixture = render();
    expect(button(fixture).disabled).toBe(true);

    type(fixture, 'not-an-email');
    expect(button(fixture).disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Enter a valid email address');

    type(fixture, 'emp@example.com');
    expect(button(fixture).disabled).toBe(false);

    type(fixture, '');
    expect(button(fixture).disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Enter your email address.');
  });

  it('links back to sign in', () => {
    expect(render().nativeElement.querySelector('a[href="/login"]')?.textContent).toContain('Back to sign in');
  });

  it('submits the trimmed email once, shows progress, then the generic confirmation from the server', () => {
    const response = new Subject<MessageResponse>();
    const fixture = render();
    resetService.requestReset.mockReturnValue(response);
    type(fixture, '  emp@example.com  ');
    submit(fixture);
    submit(fixture);

    expect(resetService.requestReset).toHaveBeenCalledTimes(1);
    expect(resetService.requestReset).toHaveBeenCalledWith('emp@example.com');
    expect(button(fixture).disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Sending…');
    expect(fixture.nativeElement.textContent).not.toContain(GENERIC);

    response.next({ message: GENERIC });
    response.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=status]')?.textContent).toContain(GENERIC);
  });

  it('reports rate limiting and unavailable email delivery without claiming success', () => {
    const fixture = render();
    resetService.requestReset.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 429 })))
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    type(fixture, 'emp@example.com');
    submit(fixture);
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('Too many reset requests');

    submit(fixture);
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('temporarily unavailable');
    expect(fixture.nativeElement.textContent).not.toContain(GENERIC);
    expect(button(fixture).disabled).toBe(false);
  });

  it('can be retried after an error', () => {
    const fixture = render();
    resetService.requestReset.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
      .mockReturnValueOnce(of({ message: GENERIC }));
    type(fixture, 'emp@example.com');
    submit(fixture);
    submit(fixture);

    expect(fixture.nativeElement.textContent).toContain(GENERIC);
  });
});
