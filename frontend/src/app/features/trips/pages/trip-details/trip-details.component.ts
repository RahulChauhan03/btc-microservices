import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { catchError, forkJoin, map, of } from 'rxjs';

import { Claim, Expense, ExpenseSummary, Trip } from '../../../../core/models/domain.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ClaimService } from '../../../../core/services/claim.service';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TripService } from '../../../../core/services/trip.service';
import { UserService } from '../../../../core/services/user.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';

interface TripDetails {
  trip: Trip;
  ownerName: string;
  expenses: Expense[];
  expenseTotal: number;
  spending: ExpenseSummary;
  claims: Claim[];
}

type LoadState = 'loading' | 'ready' | 'not-found' | 'error';

/**
 * One trip with its owner, expenses, claims and spending. trip-service decides who may read it (owner or
 * administrator); everything else is loaded with the same caller-scoped APIs, so nothing extra is exposed.
 */
@Component({
  selector: 'app-trip-details',
  imports: [RouterLink, CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, BtcLoaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trip-details.component.html',
  styleUrl: './trip-details.component.css',
})
export class TripDetailsComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly tripService = inject(TripService);
  private readonly expenseService = inject(ExpenseService);
  private readonly claimService = inject(ClaimService);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<LoadState>('loading');
  readonly details = signal<TripDetails | null>(null);
  readonly deleting = signal(false);

  readonly isOwner = computed(() => {
    const trip = this.details()?.trip;
    return !!trip && trip.ownerId === this.authService.currentUser()?.id;
  });
  readonly spentPercent = computed(() => {
    const d = this.details();
    const budget = Number(d?.trip.budget ?? 0);
    return d && budget > 0 ? Math.min(100, (Number(d.spending.total) / budget) * 100) : 0;
  });
  readonly overBudget = computed(() => {
    const d = this.details();
    return !!d && Number(d.spending.total) > Number(d.trip.budget);
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => this.load(Number(params.get('id'))));
  }

  load(id = this.details()?.trip.id ?? Number(this.route.snapshot.paramMap.get('id'))): void {
    if (!Number.isInteger(id) || id <= 0) {
      this.state.set('not-found');
      return;
    }
    this.state.set('loading');
    forkJoin({
      trip: this.tripService.getTrip(id),
      expenses: this.expenseService.listExpenses({ tripId: id, sort: 'expenseDate,desc', size: 100 }),
      spending: this.expenseService.getSummary({ tripId: id }),
      claims: this.claimService.listClaims({ tripId: id, sort: 'submittedAt,desc', size: 100 }).pipe(map((page) => page.items)),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ trip, expenses, spending, claims }) => {
          this.details.set({ trip, ownerName: '', expenses: expenses.items, expenseTotal: expenses.total, spending, claims });
          this.state.set('ready');
          this.resolveOwnerName(trip);
        },
        error: (error: unknown) =>
          this.state.set(error instanceof HttpErrorResponse && (error.status === 404 || error.status === 403) ? 'not-found' : 'error'),
      });
  }

  deleteTrip(): void {
    const d = this.details();
    if (!d || !this.isOwner() || d.expenseTotal > 0 || this.deleting()) {
      return;
    }
    this.confirmation
      .confirmDelete({ tableName: 'Trips', columnName: 'Trip', value: d.trip.tripCode })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.deleting.set(true);
        this.tripService.deleteTrip(
          d.trip.id,
          () => {
            this.toast.success('Trip deleted.');
            this.router.navigate(['/trips']);
          },
          () => this.deleting.set(false),
        );
      });
  }

  statusClass(status: string): string {
    return `status-badge status-${status.toLowerCase().replace(/_/g, '-')}`;
  }

  label(value: string): string {
    const text = value.toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  /** The owner's name: the caller themselves, or (administrators only) looked up in user-service. */
  private resolveOwnerName(trip: Trip): void {
    const me = this.authService.currentUser();
    const set = (ownerName: string) => this.details.update((d) => (d ? { ...d, ownerName } : d));
    if (trip.ownerId === null) {
      set('Unassigned (created before ownership)');
    } else if (trip.ownerId === me?.id) {
      set(`${me.name} (you)`);
    } else if (me?.role === 'ADMIN') {
      this.userService
        .getUser(trip.ownerId)
        .pipe(
          map((user) => user.name),
          catchError(() => of(`Employee #${trip.ownerId}`)),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(set);
    }
  }
}
