import { CommonModule, CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { Claim, ClaimPayload, Expense } from '../../../../core/models/domain.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ClaimService } from '../../../../core/services/claim.service';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { ToastService } from '../../../../core/services/toast.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
} from '../../../../shared/components/data-table/data-table.component';
import { StatCardComponent } from '../../../../shared/components/stat-card/stat-card.component';

@Component({
  selector: 'app-claim-management',
  imports: [
    CommonModule,
    CurrencyPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    DataTableComponent,
    StatCardComponent,
  ],
  templateUrl: './claim-management.component.html',
  styleUrl: './claim-management.component.css',
})
export class ClaimManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly claimService = inject(ClaimService);
  private readonly expenseService = inject(ExpenseService);
  private readonly authService = inject(AuthService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly claims = signal<Claim[]>([]);
  /** The caller's own expenses: a claim can only cover these (enforced by the backend). */
  readonly ownExpenses = signal<Expense[]>([]);
  readonly editingClaimId = signal<number | null>(null);
  readonly isFormPage = signal(false);
  readonly pendingClaims = computed(() => this.claims().filter((claim) => this.isAwaitingReview(claim)).length);
  readonly tableColumns: DataTableColumn<Claim>[] = [
    { key: 'claimNumber', header: 'Claim' },
    { key: 'title', header: 'Title' },
    { key: 'submittedAt', header: 'Submitted', type: 'date' },
    { key: 'claimAmount', header: 'Amount', type: 'currency' },
    { key: 'status', header: 'Status', type: 'chip' },
    { key: 'description', header: 'Description' },
  ];
  // Visibility mirrors the backend rules for a better UX; claim-service enforces them independently.
  readonly tableActions: DataTableAction<Claim>[] = [
    {
      id: 'edit',
      label: 'Edit',
      icon: 'edit',
      handler: (claim) => this.editClaim(claim),
      visible: (claim) => this.isMine(claim) && this.isAwaitingReview(claim),
    },
    {
      id: 'delete',
      label: 'Delete',
      icon: 'delete',
      handler: (claim) => this.deleteClaim(claim),
      visible: (claim) => this.isMine(claim) && this.isAwaitingReview(claim),
    },
    {
      id: 'approve',
      label: 'Approve',
      icon: 'check_circle',
      handler: (claim) => this.reviewClaim(claim, true),
      visible: (claim) => this.canReview(claim),
    },
    {
      id: 'reject',
      label: 'Reject',
      icon: 'cancel',
      handler: (claim) => this.reviewClaim(claim, false),
      visible: (claim) => this.canReview(claim),
    },
  ];

  readonly claimForm = this.fb.nonNullable.group({
    claimNumber: ['', [Validators.required, Validators.minLength(3)]],
    title: ['', [Validators.required, Validators.minLength(3)]],
    expenseIds: [[] as number[], Validators.required],
    description: ['', [Validators.required, Validators.minLength(5)]],
  });

  private readonly selectedExpenseIds = toSignal(this.claimForm.controls.expenseIds.valueChanges, {
    initialValue: [] as number[],
  });
  /** Preview only; the backend calculates the claim amount from the linked expenses. */
  readonly selectedTotal = computed(() =>
    this.ownExpenses()
      .filter((expense) => this.selectedExpenseIds().includes(expense.id))
      .reduce((sum, expense) => sum + Number(expense.amount ?? 0), 0),
  );

  constructor() {
    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));
      this.isFormPage.set(this.router.url.includes('/new') || this.router.url.includes('/edit/'));
      this.editingClaimId.set(Number.isFinite(id) && id > 0 ? id : null);
      this.patchEditingClaim();
    });
    this.loadData();
  }

  submit(): void {
    if (this.claimForm.invalid) {
      this.claimForm.markAllAsTouched();
      return;
    }

    const payload: ClaimPayload = this.claimForm.getRawValue();
    const onSaved = () => {
      this.toastService.success(this.editingClaimId() ? TOAST_MESSAGES.claims.updated : TOAST_MESSAGES.claims.created);
      this.resetForm();
      this.loadData();
      this.router.navigate(['/claims']);
    };

    if (this.editingClaimId()) {
      this.claimService.updateClaim(this.editingClaimId() as number, payload, onSaved);
      return;
    }

    this.claimService.submitClaim(payload, onSaved);
  }

  editClaim(claim: Claim): void {
    this.router.navigate(['/claims/edit', claim.id]);
  }

  private patchEditingClaim(): void {
    const claim = this.claims().find((item) => item.id === this.editingClaimId());

    if (!claim) {
      if (this.isFormPage() && !this.editingClaimId()) {
        this.resetForm();
      }
      return;
    }

    this.claimForm.patchValue({
      claimNumber: claim.claimNumber,
      title: claim.title,
      expenseIds: claim.expenseIds ?? [],
      description: claim.description ?? '',
    });
  }

  viewClaim(claim: Claim): void {
    this.toastService.info(TOAST_MESSAGES.claims.viewed(claim));
  }

  deleteClaim(claim: Claim): void {
    this.confirmationService
      .confirmDelete({
        tableName: 'Claims',
        columnName: 'Claim',
        value: claim.claimNumber,
      })
      .subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      this.claimService.deleteClaim(claim.id, () => {
        this.toastService.success(TOAST_MESSAGES.claims.deleted);
        if (this.editingClaimId() === claim.id) {
          this.resetForm();
        }
        this.loadData();
      });
      });
  }

  resetForm(): void {
    this.claimForm.reset({ claimNumber: '', title: '', expenseIds: [], description: '' });
  }

  private reviewClaim(claim: Claim, approve: boolean): void {
    const onReviewed = () => {
      this.toastService.success(approve ? TOAST_MESSAGES.claims.approved : TOAST_MESSAGES.claims.rejected);
      this.loadData();
    };
    if (approve) {
      this.claimService.approveClaim(claim.id, onReviewed);
      return;
    }
    this.claimService.rejectClaim(claim.id, onReviewed);
  }

  private isMine(claim: Claim): boolean {
    return claim.ownerId !== null && claim.ownerId === this.authService.currentUser()?.id;
  }

  private isAwaitingReview(claim: Claim): boolean {
    return claim.status === 'SUBMITTED' || claim.status === 'PENDING';
  }

  private canReview(claim: Claim): boolean {
    return (
      this.authService.hasRole('ADMIN') &&
      !this.isMine(claim) &&
      this.isAwaitingReview(claim) &&
      (claim.expenseIds?.length ?? 0) > 0
    );
  }

  private loadData(): void {
    this.claimService.getClaims(
      (claims) => {
        this.claims.set(claims);
        this.patchEditingClaim();
      },
      () => this.claims.set([]),
    );
    this.expenseService.getExpenses(
      (expenses) =>
        this.ownExpenses.set(
          // Offer only expenses that are free, or already part of the claim being edited.
          expenses.filter(
            (expense) =>
              expense.ownerId === this.authService.currentUser()?.id &&
              (expense.claimId === null || expense.claimId === this.editingClaimId()),
          ),
        ),
      () => this.ownExpenses.set([]),
    );
  }
}
