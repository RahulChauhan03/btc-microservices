import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { AuthLayoutComponent } from '../../components/auth-layout/auth-layout.component';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    AuthLayoutComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toastService = inject(ToastService);

  /** Set by the reset-password screen through navigation state (nothing sensitive is passed). */
  readonly passwordReset = history.state?.passwordReset === true;
  readonly hidePassword = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    rememberMe: [true],
  });

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const { rememberMe, ...credentials } = this.loginForm.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService.login(
      credentials,
      rememberMe,
      () => {
        this.submitting.set(false);
        this.toastService.success(TOAST_MESSAGES.auth.signedIn);
        this.router.navigate(['/dashboard']);
      },
      (error) => {
        this.submitting.set(false);
        this.errorMessage.set(LoginComponent.describe(error));
      },
    );
  }

  private static describe(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    if (status === 401 || status === 400) {
      return 'Incorrect email or password.';
    }
    if (status === 0) {
      return 'We can’t reach BTC Flow right now. Check your connection and try again.';
    }
    if (status === 429) {
      return 'Too many sign-in attempts. Wait a moment and try again.';
    }
    return 'Sign-in failed. Please try again.';
  }
}
