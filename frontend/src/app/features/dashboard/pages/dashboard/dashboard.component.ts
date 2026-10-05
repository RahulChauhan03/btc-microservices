import { CurrencyPipe, DatePipe, NgTemplateOutlet, formatCurrency } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';

import {
  Claim,
  ClaimSummary,
  Expense,
  ExpenseSummary,
  PagedResult,
  Trip,
  TripSummary,
  UserStats,
} from '../../../../core/models/domain.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ClaimService } from '../../../../core/services/claim.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { TripService } from '../../../../core/services/trip.service';
import { UserService } from '../../../../core/services/user.service';
import { describeHttpError } from '../../../../core/http/describe-error';
import { BarItem, BarListComponent } from '../../../../shared/components/bar-list/bar-list.component';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';
import { StatCardComponent } from '../../../../shared/components/stat-card/stat-card.component';

/** Every section loads independently: null means it could not be loaded (see DashboardComponent.failed). */
interface DashboardData {
  trips: TripSummary | null;
  upcomingTrips: Trip[] | null;
  expenses: ExpenseSummary | null;
  claims: ClaimSummary | null;
  recentExpenses: Expense[] | null;
  recentClaims: Claim[] | null;
  /** Administrators only. */
  pendingClaims: PagedResult<Claim> | null;
  users: UserStats | null;
}

type Section = keyof DashboardData;

/** Which backend each section comes from, for error messages. */
const SECTION_SERVICE: Record<Section, string> = {
  trips: 'trip service',
  upcomingTrips: 'trip service',
  expenses: 'expense service',
  recentExpenses: 'expense service',
  claims: 'claims service',
  recentClaims: 'claims service',
  pendingClaims: 'claims service',
  users: 'user service',
};

interface Activity {
  icon: string;
  title: string;
  detail: string;
  date: string;
  link: string;
}

type LoadState = 'loading' | 'ready' | 'error';

/**
 * Role-aware dashboard. Every figure comes from the backend for the caller's scope: employees see their own
 * data, administrators company-wide data (enforced by each service, not by this page).
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, CurrencyPipe, DatePipe, NgTemplateOutlet, MatButtonModule, MatIconModule, StatCardComponent, BarListComponent, BtcLoaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  private readonly tripService = inject(TripService);
  private readonly expenseService = inject(ExpenseService);
  private readonly claimService = inject(ClaimService);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly today = new Date();
  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');
  readonly state = signal<LoadState>('loading');
  readonly data = signal<DashboardData | null>(null);
  /** Sections whose request failed, with a message naming the service; the rest of the page still renders. */
  readonly failed = signal<Partial<Record<Section, string>>>({});
  readonly failedServices = computed(() => [...new Set(Object.values(this.failed()))]);

  readonly greeting = computed(() => {
    const hour = this.today.getHours();
    const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
    const firstName = (this.authService.currentUser()?.name ?? '').trim().split(/\s+/)[0];
    return firstName ? `${part}, ${firstName}` : part;
  });

  readonly approvedCount = computed(
    () => this.data()?.claims?.byStatus.find((row) => row.status === 'APPROVED')?.count ?? 0,
  );

  readonly categoryBars = computed<BarItem[]>(() =>
    (this.data()?.expenses?.byCategory ?? []).map((row) => ({
      label: this.label(row.category),
      value: Number(row.total),
      display: this.money(row.total),
      hint: `${row.count} expense${row.count === 1 ? '' : 's'}`,
    })),
  );

  readonly monthBars = computed<BarItem[]>(() =>
    (this.data()?.expenses?.byMonth ?? []).map((row) => ({
      label: new Date(row.year, row.month - 1, 1).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }),
      value: Number(row.total),
      display: this.money(row.total),
      hint: `${row.count} expense${row.count === 1 ? '' : 's'}`,
    })),
  );

  readonly claimStatusRows = computed(() =>
    (this.data()?.claims?.byStatus ?? []).map((row) => ({ ...row, label: this.label(row.status) })),
  );

  readonly activity = computed<Activity[]>(() => {
    const data = this.data();
    if (!data) {
      return [];
    }
    const expenses: Activity[] = (data.recentExpenses ?? []).map((expense) => ({
      icon: 'receipt_long',
      title: expense.title,
      detail: `Expense · ${this.money(expense.amount)}`,
      date: expense.createdAt,
      link: '/expenses',
    }));
    const claims: Activity[] = (data.recentClaims ?? []).map((claim) => ({
      icon: 'fact_check',
      title: claim.title,
      detail: `Claim ${claim.claimNumber} · ${this.label(claim.status)}`,
      date: claim.reviewedAt ?? claim.submittedAt,
      link: '/claims',
    }));
    return [...expenses, ...claims].sort((a, b) => (b.date ?? '').localeCompare(a.date ?? '')).slice(0, 6);
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    const admin = this.isAdmin();
    const recent = { page: 0, size: 5 };
    const sources: { [K in keyof DashboardData]: Observable<DashboardData[K]> } = {
      trips: this.tripService.getSummary(),
      upcomingTrips: this.tripService.listTrips({ upcoming: true, sort: 'startDate,asc', size: 5 }).pipe(map((page) => page.items)),
      expenses: this.expenseService.getSummary(),
      claims: this.claimService.getSummary(),
      recentExpenses: this.expenseService.listExpenses({ ...recent, sort: 'createdAt,desc' }).pipe(map((page) => page.items)),
      recentClaims: this.claimService.listClaims({ ...recent, sort: 'submittedAt,desc' }).pipe(map((page) => page.items)),
      pendingClaims: admin
        ? this.claimService.listClaims({ status: ['SUBMITTED', 'PENDING'], sort: 'submittedAt,asc', size: 5 })
        : of(null),
      users: admin ? this.userService.getStats() : of(null),
    };
    const failures: Partial<Record<Section, string>> = {};
    // Each source is typed by `sources`; a failed one becomes null and is recorded in `failures`.
    const tolerant = {} as Record<Section, Observable<unknown>>;
    for (const key of Object.keys(sources) as Section[]) {
      tolerant[key] = (sources[key] as Observable<unknown>).pipe(
        catchError((error: unknown) => {
          failures[key] = describeHttpError(error, SECTION_SERVICE[key]);
          return of(null);
        }),
      );
    }
    (forkJoin(tolerant) as Observable<DashboardData>)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        this.failed.set(failures);
        this.data.set(data);
        // Only when nothing at all could be loaded is the whole page an error.
        const requested = (Object.keys(sources) as Section[]).filter((key) => admin || (key !== 'pendingClaims' && key !== 'users'));
        this.state.set(requested.every((key) => failures[key]) ? 'error' : 'ready');
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

