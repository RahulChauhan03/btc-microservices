import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';

@Component({
  selector: 'app-login',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toastService = inject(ToastService);

  readonly hidePassword = signal(true);
  readonly submitting = signal(false);

  readonly loginForm = this.fb.nonNullable.group({
    email: ['admin@btcsystem.com', [Validators.required, Validators.email]],
    password: ['Password@123', [Validators.required, Validators.minLength(8)]],
    rememberMe: [true],
  });

  submit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const { rememberMe, ...credentials } = this.loginForm.getRawValue();
    this.submitting.set(true);

    this.authService.login(
      credentials,
      rememberMe,
      () => {
        this.submitting.set(false);
        this.toastService.success(TOAST_MESSAGES.auth.signedIn);
        this.router.navigate(['/dashboard']);
      },
      () => this.submitting.set(false),
    );
  }
}
