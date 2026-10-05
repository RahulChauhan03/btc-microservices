import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { User } from '../../core/models/domain.models';
import { ProfileService } from '../../core/services/profile.service';
import { ToastService } from '../../core/services/toast.service';
import { BtcLoaderComponent } from '../../shared/components/btc-loader/btc-loader.component';

function maxBytes(control: AbstractControl): ValidationErrors | null {
  return new TextEncoder().encode(String(control.value ?? '')).length > 72 ? { tooLong: true } : null;
}

function matches(control: AbstractControl): ValidationErrors | null {
  const next = control.parent?.get('newPassword')?.value;
  return control.value && control.value !== next ? { mismatch: true } : null;
}

const message = (error: unknown, fallback: string) =>
  error instanceof HttpErrorResponse && error.error?.message ? (error.error.message as string) : fallback;

/** Profile & settings: name and phone, plus a password change that requires the current password. */
@Component({
  selector: 'app-profile',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, BtcLoaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent {
  private readonly profile = inject(ProfileService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly user = signal<User | null>(null);
  readonly savingProfile = signal(false);
  readonly profileError = signal<string | null>(null);
  readonly savingPassword = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly show = signal({ current: false, next: false });

  readonly profileForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    phone: ['', [Validators.required, Validators.maxLength(255)]],
  });
  readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8), maxBytes]],
    confirmPassword: ['', [Validators.required, matches]],
  });

  constructor() {
    this.passwordForm.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.passwordForm.controls.confirmPassword.updateValueAndValidity({ emitEvent: false }));
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.profile.me().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (user) => {
        this.user.set(user);
        this.profileForm.reset({ name: user.name, phone: user.phone });
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  toggle(field: 'current' | 'next'): void {
    this.show.update((value) => ({ ...value, [field]: !value[field] }));
  }

  saveProfile(): void {
    if (this.profileForm.invalid || this.savingProfile()) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const { name, phone } = this.profileForm.getRawValue();
    this.savingProfile.set(true);
    this.profileError.set(null);
    this.profile.update(name.trim(), phone.trim()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (user) => {
        this.savingProfile.set(false);
        this.user.set(user);
        this.profileForm.reset({ name: user.name, phone: user.phone });
        this.toast.success('Profile updated.');
      },
      error: (error: unknown) => {
        this.savingProfile.set(false);
        this.profileError.set(message(error, 'Your profile could not be saved.'));
      },
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid || this.savingPassword()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.getRawValue();
    this.savingPassword.set(true);
    this.passwordError.set(null);
    this.profile.changePassword(currentPassword, newPassword, confirmPassword).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordForm.reset();
        this.toast.success('Password changed. Use it the next time you sign in.');
      },
      error: (error: unknown) => {
        this.savingPassword.set(false);
        this.passwordError.set(
          error instanceof HttpErrorResponse && error.status === 429
            ? 'Too many attempts. Please wait a few minutes and try again.'
            : message(error, 'Your password could not be changed.'),
        );
      },
    });
  }
}
