import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { Reimbursement, ReimbursementStatus } from '../../../../core/models/domain.models';
import { describeHttpError } from '../../../../core/http/describe-error';
import { AuthService } from '../../../../core/services/auth.service';
import { ReimbursementService } from '../../../../core/services/reimbursement.service';
import { ToastService } from '../../../../core/services/toast.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';

const PAGE_SIZE = 20;

/**
 * Reimbursement tracking for approved claims. This records what finance reports; BTC Flow moves no money.
 * Employees see their own; administrators record status changes (never on their own claims). The backend
 * enforces every rule shown here.
 */
@Component({
  selector: 'app-reimbursements',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, BtcLoaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reimbursements.component.html',
  styleUrl: './reimbursements.component.css',
})
export class ReimbursementsComponent {
  private readonly service = inject(ReimbursementService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly today = new Date().toISOString().slice(0, 10);
  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly statusFilter = new FormControl<ReimbursementStatus | ''>('', { nonNullable: true });
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly rows = signal<Reimbursement[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / PAGE_SIZE)));
  readonly editing = signal<Reimbursement | null>(null);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly loadError = signal('');
  readonly form = inject(FormBuilder).nonNullable.group({
    status: ['' as ReimbursementStatus | '', Validators.required],
    paymentDate: [''],
    paymentReference: ['', [Validators.maxLength(100), Validators.pattern(/^[A-Za-z0-9._/-]*$/)]],
  });

  constructor() {
    this.statusFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.load(0));
    this.load(0);
  }

  load(page = this.page()): void {
    this.state.set('loading');
    this.service
      .list({ status: this.statusFilter.value, page, size: PAGE_SIZE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.rows.set(result.items);
          this.total.set(result.total);
          this.page.set(page);
          this.state.set('ready');
        },
        error: (error: unknown) => {
          this.loadError.set(describeHttpError(error, 'claims service'));
          this.state.set('error');
        },
      });
  }

  /** Administrators may update others' reimbursements while a next status exists. */
  canUpdate(row: Reimbursement): boolean {
    return this.isAdmin() && row.allowedNext.length > 0 && row.ownerId !== this.auth.currentUser()?.id;
  }

  startUpdate(row: Reimbursement): void {
    this.formError.set(null);
    this.form.reset({ status: row.allowedNext[0], paymentDate: this.today, paymentReference: '' });
    this.editing.set(row);
  }

  save(): void {
    const row = this.editing();
    const value = this.form.getRawValue();
    if (!row || this.form.invalid || !value.status) {
      this.form.markAllAsTouched();
      return;
    }
    const paid = value.status === 'PAID';
    if (paid && (!value.paymentDate || !value.paymentReference.trim())) {
      this.formError.set('A paid reimbursement needs a payment date and a payment reference.');
      return;
    }
    if (paid && value.paymentDate > this.today) {
      this.formError.set('The payment date cannot be in the future.');
      return;
    }
    this.saving.set(true);
    this.service
      .update(row.claimId, {
        status: value.status,
        paymentDate: paid ? value.paymentDate : null,
        paymentReference: paid ? value.paymentReference.trim() : null,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.editing.set(null);
          this.rows.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
          this.toast.success(`Reimbursement for ${updated.claimNumber ?? 'the claim'} is now ${this.label(updated.status).toLowerCase()}.`);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.formError.set(error instanceof HttpErrorResponse && error.error?.message ? error.error.message : 'The update could not be saved.');
        },
      });
  }

  label(status: string): string {
    return status.charAt(0) + status.slice(1).toLowerCase();
  }
}
