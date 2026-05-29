import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { Claim, ClaimPayload } from '../../../../core/models/domain.models';
import { ClaimService } from '../../../../core/services/claim.service';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ToastService } from '../../../../core/services/toast.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
} from '../../../../shared/components/data-table/data-table.component';

@Component({
  selector: 'app-claim-management',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    DataTableComponent,
  ],
  templateUrl: './claim-management.component.html',
  styleUrl: './claim-management.component.css',
})
export class ClaimManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly claimService = inject(ClaimService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly claims = signal<Claim[]>([]);
  readonly editingClaimId = signal<number | null>(null);
  readonly isFormPage = signal(false);
  readonly pendingClaims = computed(
    () => this.claims().filter((claim) => ['SUBMITTED', 'PENDING'].includes(claim.status)).length,
  );
  readonly tableColumns: DataTableColumn<Claim>[] = [
    { key: 'claimNumber', header: 'Claim' },
    { key: 'title', header: 'Title' },
    { key: 'submittedAt', header: 'Submitted', type: 'date' },
    { key: 'claimAmount', header: 'Amount', type: 'currency' },
    { key: 'status', header: 'Status', type: 'chip' },
    { key: 'description', header: 'Description' },
  ];
  readonly tableActions: DataTableAction<Claim>[] = [
    { id: 'edit', label: 'Edit', icon: 'edit', handler: (claim) => this.editClaim(claim) },
    { id: 'delete', label: 'Delete', icon: 'delete', handler: (claim) => this.deleteClaim(claim) },
  ];

  readonly claimForm = this.fb.nonNullable.group({
    claimNumber: ['', [Validators.required, Validators.minLength(3)]],
    title: ['', [Validators.required, Validators.minLength(3)]],
    claimAmount: [0, [Validators.required, Validators.min(1)]],
    status: ['PENDING' as Claim['status'], Validators.required],
    description: ['', [Validators.required, Validators.minLength(5)]],
  });

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

    const payload = this.claimForm.getRawValue() as ClaimPayload;
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
      claimAmount: claim.claimAmount,
      status: claim.status,
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
    this.claimForm.reset({ claimNumber: '', title: '', claimAmount: 0, status: 'PENDING', description: '' });
  }

  private loadData(): void {
    this.claimService.getClaims(
      (claims) => {
        this.claims.set(claims);
        this.patchEditingClaim();
      },
      () => this.claims.set([]),
    );
  }
}
