import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { PasswordResetService } from '../../../../core/services/password-reset.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';
import { AuthLayoutComponent } from '../../components/auth-layout/auth-layout.component';

type ResetState = 'idle' | 'saving' | 'invalid' | 'expired' | 'error';

/** Mirrors user-service: BCrypt uses at most 72 bytes, and a password of spaces only is rejected. */
function passwordRules(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  if (!value) {
    return null;
  }
  if (!value.trim()) {
    return { blank: true };
  }
  return new TextEncoder().encode(value).length > 72 ? { tooLong: true } : null;
}

function matchesNewPassword(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.parent?.get('newPassword')?.value;
  return control.value && newPassword !== control.value ? { mismatch: true } : null;
}

/**
 * Completes a reset from the emailed link (/reset-password?token=...). The token is read once, removed from
 * the address bar and browser history, and kept only in this component's memory: never stored or logged.
 * The API never signs the user in; on success the form is cleared and the user is sent to sign in.
 */
@Component({
  selector: 'app-reset-password',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    AuthLayoutComponent,
    BtcLoaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reset-password.component.html',
})
export class ResetPasswordComponent {
  private readonly passwordReset = inject(PasswordResetService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private token: string | null = this.route.snapshot.queryParamMap.get('token');

  readonly hasToken = signal(!!this.token);
  readonly state = signal<ResetState>('idle');
  readonly errorMessage = signal('');
  readonly showNew = signal(false);
  readonly showConfirm = signal(false);
  readonly form = inject(FormBuilder).nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), passwordRules]],
    confirmPassword: ['', [Validators.required, matchesNewPassword]],
  });

  constructor() {
    if (this.token) {
      this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    }
    this.form.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false }));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.token || this.state() === 'saving') {
      return;
    }

    const { newPassword, confirmPassword } = this.form.getRawValue();
    this.state.set('saving');
    this.passwordReset
      .resetPassword(this.token, newPassword, confirmPassword)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.token = null;
          this.form.reset();
          this.router.navigate(['/login'], { replaceUrl: true, state: { passwordReset: true } });
        },
        error: (error: unknown) => this.handleError(error),
      });
  }

  private handleError(error: unknown): void {
    const response = error instanceof HttpErrorResponse ? error : null;
    switch (response?.status) {
      case 404:
        this.token = null;
        this.state.set('invalid');
        return;
      case 410:
        this.token = null;
        this.state.set('expired');
        return;
      case 400:
        this.errorMessage.set(response.error?.message ?? 'The new password doesn’t meet the requirements.');
        break;
      case 429:
        this.errorMessage.set('Too many attempts. Please wait a few minutes and try again.');
        break;
      case 0:
        this.errorMessage.set('We can’t reach BTC Flow right now. Check your connection and try again.');
        break;
      default:
        this.errorMessage.set('We couldn’t update your password. Please try again.');
    }
    this.state.set('error');
  }
}
