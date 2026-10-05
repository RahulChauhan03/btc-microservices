import { DatePipe, formatCurrency } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { ClaimStatusReport, ExpenseSummary, Trip, TripSpend } from '../../../../core/models/domain.models';
import { describeHttpError } from '../../../../core/http/describe-error';
import { ReportRange, ReportService } from '../../../../core/services/report.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TripService } from '../../../../core/services/trip.service';
import { BarItem, BarListComponent } from '../../../../shared/components/bar-list/bar-list.component';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';
import { DataTableColumn, DataTableComponent, DataTablePageChange } from '../../../../shared/components/data-table/data-table.component';
import { StatCardComponent } from '../../../../shared/components/stat-card/stat-card.component';

const MAX_DAYS = 731;

interface TripRow extends TripSpend {
  trip: Trip | null;
}

function isoDate(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/** Mirrors the backend bound: from <= to and at most two years. */
function validRange(group: AbstractControl): ValidationErrors | null {
  const { from, to } = group.value as ReportRange;
  if (!from || !to) {
    return null;
  }
  const days = (Date.parse(to) - Date.parse(from)) / 86_400_000;
  return days < 0 ? { order: true } : days > MAX_DAYS ? { tooLong: true } : null;
}

/** Administrator reports. The page only displays what expense- and claim-service aggregate. */
@Component({
  selector: 'app-reports',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    BarListComponent,
    BtcLoaderComponent,
    DataTableComponent,
    StatCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.css',
})
export class ReportsComponent {
  private readonly reports = inject(ReportService);
  private readonly trips = inject(TripService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly presets = [
    { label: 'Last 3 months', months: 3 },
    { label: 'Last 12 months', months: 12 },
    { label: 'This year', months: 0 },
  ];
  readonly form = inject(FormBuilder).nonNullable.group(
    { from: ['', Validators.required], to: ['', Validators.required] },
    { validators: validRange },
  );

  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly range = signal<ReportRange | null>(null);
  readonly expenses = signal<ExpenseSummary | null>(null);
  readonly claims = signal<ClaimStatusReport | null>(null);
  readonly tripRows = signal<TripRow[]>([]);
  readonly tripTotal = signal(0);
  readonly tripPage = signal(0);
  readonly tripPageSize = signal(10);
  readonly statusColumns: DataTableColumn<ClaimStatusReport['byStatus'][number]>[] = [
    { key: 'status', header: 'Status', type: 'chip', value: (row) => this.label(row.status) },
    { key: 'count', header: 'Claims', type: 'text' },
    { key: 'total', header: 'Amount', type: 'currency' },
  ];
  readonly tripColumns: DataTableColumn<TripRow>[] = [
    { key: 'trip', header: 'Trip', type: 'link', value: (row) => row.trip?.tripCode ?? `Trip #${row.tripId}`, link: (row) => ['/trips/view', row.tripId] },
    { key: 'destination', header: 'Destination', value: (row) => row.trip?.destination ?? '—' },
    { key: 'count', header: 'Expenses', mobilePriority: 'secondary' },
    { key: 'total', header: 'Spent', type: 'currency' },
    { key: 'budget', header: 'Budget', type: 'currency', value: (row) => row.trip?.budget ?? null, emptyValue: '—', mobilePriority: 'secondary' },
  ];
  readonly exporting = signal(false);
  /** Per-section failures: expense and claim reports come from different services and load independently. */
  readonly expensesError = signal<string | null>(null);
  readonly claimsError = signal<string | null>(null);
  readonly tripsError = signal<string | null>(null);

  readonly monthBars = computed<BarItem[]>(() =>
    (this.expenses()?.byMonth ?? []).map((row) => ({
      label: new Date(row.year, row.month - 1, 1).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }),
      value: Number(row.total),
      display: this.money(row.total),
      hint: `${row.count} expense${row.count === 1 ? '' : 's'}`,
    })),
  );
  readonly categoryBars = computed<BarItem[]>(() =>
    (this.expenses()?.byCategory ?? []).map((row) => ({
      label: this.label(row.category),
      value: Number(row.total),
      display: this.money(row.total),
      hint: `${row.count} expense${row.count === 1 ? '' : 's'}`,
    })),
  );
  readonly approvedAmount = computed(
    () => this.claims()?.byStatus.find((row) => row.status === 'APPROVED')?.total ?? 0,
  );
  constructor() {
    this.applyPreset(12);
  }

  applyPreset(months: number): void {
    const today = new Date();
    const from = months === 0 ? new Date(today.getFullYear(), 0, 1) : new Date(today.getFullYear(), today.getMonth() - months + 1, 1);
    this.form.setValue({ from: isoDate(from), to: isoDate(today) });
    this.run();
  }

  run(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const range = this.form.getRawValue();
    this.range.set(range);
    this.state.set('loading');
    this.expensesError.set(null);
    this.claimsError.set(null);
    forkJoin({
      expenses: this.reports.expenseSummary(range).pipe(
        catchError((error: unknown) => {
          this.expensesError.set(describeHttpError(error, 'expense service'));
          return of(null);
        }),
      ),
      claims: this.reports.claimStatus(range).pipe(
        catchError((error: unknown) => {
          this.claimsError.set(describeHttpError(error, 'claims service'));
          return of(null);
        }),
      ),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ expenses, claims }) => {
        this.expenses.set(expenses);
        this.claims.set(claims);
        // Real failures are shown per section; the page is an error only when nothing could be loaded.
        this.state.set(expenses || claims ? 'ready' : 'error');
        if (expenses) {
          this.loadTrips(0);
        }
      });
  }

  loadTrips(page: number, pageSize = this.tripPageSize()): void {
    const range = this.range();
    if (!range) {
      return;
    }
    this.reports
      .spendingByTrip(range, page, pageSize)
      .pipe(
        // One batch lookup for the trips on this page (no request per row).
        switchMap((result) =>
          (result.items.length
            ? this.trips.listTrips({ ids: result.items.map((row) => String(row.tripId)), size: result.items.length })
            : of({ items: [] as Trip[], total: 0 })
          ).pipe(
            map((trips) => ({
              total: result.total,
              rows: result.items.map((row) => ({ ...row, trip: trips.items.find((trip) => trip.id === row.tripId) ?? null })),
            })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ total, rows }) => {
          this.tripsError.set(null);
          this.tripRows.set(rows);
          this.tripTotal.set(total);
          this.tripPage.set(page);
          this.tripPageSize.set(pageSize);
        },
        error: (error: unknown) => this.tripsError.set(describeHttpError(error, 'expense or trip service')),
      });
  }

    onTripPageChange(event: DataTablePageChange): void {
      this.loadTrips(event.pageIndex, event.pageSize);
    }

  exportCsv(): void {
    const range = this.range();
    if (!range || this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.reports
      .exportCsv(range)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `btc-expenses-${range.from}-to-${range.to}.csv`;
          link.click();
          URL.revokeObjectURL(url);
          this.exporting.set(false);
        },
        error: (error: unknown) => {
          this.exporting.set(false);
          this.toast.error(error instanceof HttpErrorResponse && error.status === 400
            ? 'The export was refused: a range may contain at most 50,000 expenses and two years. Narrow the dates.'
            : describeHttpError(error, 'expense service'));
        },
      });
  }

  statusClass(status: string): string {
    return `status-badge status-${status.toLowerCase().replace(/_/g, '-')}`;
  }

  label(value: string): string {
    const text = value.toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  private money(value: number): string {
    return formatCurrency(Number(value ?? 0), 'en-US', '$', 'USD');
  }
}
