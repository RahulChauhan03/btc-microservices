import { CurrencyPipe, DatePipe, KeyValuePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { ExpenseCategory, TravelPolicy, TravelPolicyPayload } from '../../../../core/models/domain.models';
import { describeHttpError } from '../../../../core/http/describe-error';
import { AuthService } from '../../../../core/services/auth.service';
import { PolicyService } from '../../../../core/services/policy.service';
import { ToastService } from '../../../../core/services/toast.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';

export const CATEGORIES: { key: ExpenseCategory; label: string }[] = [
  { key: 'TRAVEL', label: 'Travel' },
  { key: 'HOTEL', label: 'Hotel' },
  { key: 'MEAL', label: 'Meals' },
  { key: 'TRANSPORT', label: 'Transport' },
  { key: 'OTHER', label: 'Other' },
];

/** Optional positive amount with at most two decimals (the backend uses decimal(12,2)). */
const amount: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  if (value === null || value === '' || value === undefined) {
    return null;
  }
  return Number(value) > 0 && /^\d{1,10}(\.\d{1,2})?$/.test(String(value)) ? null : { amount: true };
};

const period: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const { effectiveFrom, effectiveTo } = group.value as { effectiveFrom: string; effectiveTo: string };
  return effectiveFrom && effectiveTo && effectiveTo < effectiveFrom ? { period: true } : null;
};

/** Company travel policy: employees read it; administrators maintain it. The backend enforces both. */
@Component({
  selector: 'app-travel-policy',
  imports: [CurrencyPipe, DatePipe, KeyValuePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, BtcLoaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './travel-policy.component.html',
  styleUrl: './travel-policy.component.css',
})
export class TravelPolicyComponent {
  private readonly policies = inject(PolicyService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  readonly categories = CATEGORIES;
  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly list = signal<TravelPolicy[]>([]);
  readonly current = computed(() => this.list().find((policy) => policy.active) ?? null);
  readonly others = computed(() => this.list().filter((policy) => !policy.active));
  /** null = form closed, 0 = new policy, otherwise the id being edited. */
  readonly editing = signal<number | null>(null);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly loadError = signal('');

  readonly form = this.fb.nonNullable.group(
    {
      name: ['', [Validators.required, Validators.maxLength(100)]],
      effectiveFrom: ['', Validators.required],
      effectiveTo: [''],
      tripLimit: ['', amount],
      limits: this.fb.nonNullable.group(
        Object.fromEntries(CATEGORIES.map((category) => [category.key, ['', amount]])) as Record<ExpenseCategory, [string, ValidatorFn]>,
      ),
    },
    { validators: period },
  );

  constructor() {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.policies.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (list) => {
        this.list.set(list);
        this.state.set('ready');
      },
      error: (error: unknown) => {
        this.loadError.set(describeHttpError(error, 'expense service'));
        this.state.set('error');
      },
    });
  }

  startNew(): void {
    this.form.reset();
    this.formError.set(null);
    this.editing.set(0);
  }

  edit(policy: TravelPolicy): void {
    this.formError.set(null);
    this.form.reset({
      name: policy.name,
      effectiveFrom: policy.effectiveFrom,
      effectiveTo: policy.effectiveTo ?? '',
      tripLimit: policy.tripLimit === null ? '' : String(policy.tripLimit),
      limits: Object.fromEntries(CATEGORIES.map((c) => [c.key, policy.categoryLimits[c.key]?.toString() ?? ''])),
    });
    this.editing.set(policy.id);
  }

  cancel(): void {
    this.editing.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const payload: TravelPolicyPayload = {
      name: value.name.trim(),
      currency: 'USD',
      effectiveFrom: value.effectiveFrom,
      effectiveTo: value.effectiveTo || null,
      tripLimit: value.tripLimit === '' ? null : Number(value.tripLimit),
      categoryLimits: Object.fromEntries(
        Object.entries(value.limits).filter(([, limit]) => limit !== '').map(([key, limit]) => [key, Number(limit)]),
      ),
    };
    const id = this.editing();
    this.saving.set(true);
    this.formError.set(null);
    (id ? this.policies.update(id, payload) : this.policies.create(payload)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(null);
        this.toast.success(id ? 'Travel policy updated.' : 'Travel policy created.');
        this.load();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.formError.set(
          error instanceof HttpErrorResponse && error.error?.message ? error.error.message : 'The policy could not be saved.',
        );
      },
    });
  }

  categoryLabel(key: string): string {
    return CATEGORIES.find((category) => category.key === key)?.label ?? key;
  }
}
