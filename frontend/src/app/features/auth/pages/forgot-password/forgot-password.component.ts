import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { PasswordResetService } from '../../../../core/services/password-reset.service';
import { AuthLayoutComponent } from '../../components/auth-layout/auth-layout.component';

type RequestState = 'idle' | 'sending' | 'sent' | 'error';

const GENERIC_CONFIRMATION = 'If an account exists for that email, you will receive password-reset instructions.';

/**
 * Requests a reset link. The confirmation appears only after user-service accepted the request, and it is the
 * same for every address, so it never reveals whether an account exists.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, AuthLayoutComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './forgot-password.component.html',
})
export class ForgotPasswordComponent {
  private readonly passwordReset = inject(PasswordResetService);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<RequestState>('idle');
  readonly sentTo = signal('');
  readonly confirmation = signal(GENERIC_CONFIRMATION);
  readonly errorMessage = signal('');
  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.state() === 'sending') {
      return;
    }

    const email = this.form.controls.email.value.trim();
    this.state.set('sending');
    this.passwordReset
      .requestReset(email)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.confirmation.set(response?.message || GENERIC_CONFIRMATION);
          this.sentTo.set(email);
          this.state.set('sent');
        },
        error: (error: unknown) => {
          this.errorMessage.set(ForgotPasswordComponent.describe(error));
          this.state.set('error');
        },
      });
  }

  startOver(): void {
    this.form.reset();
    this.state.set('idle');
  }

  private static describe(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    if (status === 429) {
      return 'Too many reset requests. Please wait a few minutes before trying again.';
    }
    if (status === 503) {
      return 'Password reset is temporarily unavailable. Please contact your administrator.';
    }
    if (status === 0) {
      return 'We can’t reach BTC Flow right now. Check your connection and try again.';
    }
    if (status === 400) {
      return 'Enter a valid email address.';
    }
    return 'We couldn’t process your request. Please try again.';
  }
}
