import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AuditEntry, AuditSource } from '../../../../core/models/domain.models';
import { describeHttpError } from '../../../../core/http/describe-error';
import { AuditService } from '../../../../core/services/audit.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';
import { DataTableColumn, DataTableComponent, DataTablePageChange } from '../../../../shared/components/data-table/data-table.component';

/** What each service audits (see the backend audit modules). */
export const AUDIT_SOURCES: { key: AuditSource; label: string; service: string; actions: string[] }[] = [
  { key: 'claims', label: 'Claims & reimbursements', service: 'claims service', actions: ['CLAIM_APPROVED', 'CLAIM_REJECTED', 'REIMBURSEMENT_STATUS_CHANGED'] },
  { key: 'users', label: 'Users', service: 'user service', actions: ['USER_CREATED', 'USER_ROLE_CHANGED', 'USER_DELETED', 'USER_PASSWORD_CHANGED', 'USER_PASSWORD_RESET'] },
  { key: 'expenses', label: 'Travel policy', service: 'expense service', actions: ['TRAVEL_POLICY_CREATED', 'TRAVEL_POLICY_UPDATED'] },
];

/** Administrator-only, read-only audit search, one source service at a time. */
@Component({
  selector: 'app-audit-log',
  imports: [ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, BtcLoaderComponent, DataTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.css',
})
export class AuditLogComponent {
  private readonly audit = inject(AuditService);
  private readonly destroyRef = inject(DestroyRef);

  readonly sources = AUDIT_SOURCES;
  readonly source = signal<AuditSource>('claims');
  readonly actions = computed(() => AUDIT_SOURCES.find((s) => s.key === this.source())?.actions ?? []);
  readonly filters = inject(FormBuilder).nonNullable.group(
    { action: '', actorId: ['', Validators.pattern(/^\s*\d*\s*$/)], from: '', to: '' },
    { validators: (group) => {
      const { from, to } = group.value as { from: string; to: string };
      return from && to && from > to ? { order: true } : null;
    } },
  );
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly rows = signal<AuditEntry[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly pageSize = signal(25);
  readonly errorMessage = signal('');
  readonly tableColumns: DataTableColumn<AuditEntry>[] = [
    { key: 'createdAt', header: 'When', type: 'date', mobilePriority: 'secondary' },
    { key: 'actorId', header: 'Actor', value: (entry) => `#${entry.actorId}`, mobilePriority: 'secondary' },
    { key: 'action', header: 'Action', value: (entry) => this.label(entry.action) },
    { key: 'target', header: 'Target', value: (entry) => `${entry.targetType.toLowerCase()} #${entry.targetId}`, mobilePriority: 'secondary' },
    { key: 'summary', header: 'Summary', mobilePriority: 'secondary' },
  ];

  constructor() {
    this.load(0);
  }

  setSource(source: AuditSource): void {
    this.source.set(source);
    this.filters.controls.action.setValue('');
    this.load(0);
  }

  load(page = this.page(), pageSize = this.pageSize()): void {
    if (this.filters.invalid) {
      this.filters.markAllAsTouched();
      return;
    }
    const { action, actorId, from, to } = this.filters.getRawValue();
    this.state.set('loading');
    this.audit
      .search(this.source(), { action, actorId: actorId.trim(), from, to, page, size: pageSize, sort: 'createdAt,desc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const lastPage = Math.max(0, Math.ceil(result.total / pageSize) - 1);
          if (page > lastPage) {
            this.load(lastPage, pageSize);
            return;
          }
          this.rows.set(result.items);
          this.total.set(result.total);
          this.page.set(page);
          this.pageSize.set(pageSize);
          this.state.set('ready');
        },
        error: (error: unknown) => {
          this.errorMessage.set(describeHttpError(error, AUDIT_SOURCES.find((s) => s.key === this.source())!.service));
          this.state.set('error');
        },
      });
  }

  onTablePageChange(event: DataTablePageChange): void {
    this.load(event.pageIndex, event.pageSize);
  }

  label(action: string): string {
    const text = action.toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
