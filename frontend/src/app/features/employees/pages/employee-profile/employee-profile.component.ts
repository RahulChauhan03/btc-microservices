import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin, map } from 'rxjs';

import { Claim, ClaimSummary, Expense, ExpenseSummary, Trip, TripSummary, User } from '../../../../core/models/domain.models';
import { ClaimService } from '../../../../core/services/claim.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { TripService } from '../../../../core/services/trip.service';
import { UserService } from '../../../../core/services/user.service';
import { BtcLoaderComponent } from '../../../../shared/components/btc-loader/btc-loader.component';
import { StatCardComponent } from '../../../../shared/components/stat-card/stat-card.component';

interface Profile {
  user: User;
  trips: TripSummary;
  expenses: ExpenseSummary;
  claims: ClaimSummary;
  recentTrips: Trip[];
  recentExpenses: Expense[];
  recentClaims: Claim[];
}

/**
 * One employee as seen by an administrator. Every request uses the ownerId filter, which the services honour
 * for administrators only (employees asking for someone else get 403).
 */
@Component({
  selector: 'app-employee-profile',
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatIconModule, BtcLoaderComponent, StatCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-profile.component.html',
  styleUrl: './employee-profile.component.css',
})
export class EmployeeProfileComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly users = inject(UserService);
  private readonly tripService = inject(TripService);
  private readonly expenseService = inject(ExpenseService);
  private readonly claimService = inject(ClaimService);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<'loading' | 'ready' | 'not-found' | 'error'>('loading');
  readonly profile = signal<Profile | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => this.load(Number(params.get('id'))));
  }

  load(id = Number(this.route.snapshot.paramMap.get('id'))): void {
    if (!Number.isInteger(id) || id <= 0) {
      this.state.set('not-found');
      return;
    }
    this.state.set('loading');
    const recent = { ownerId: id, size: 5 };
    forkJoin({
      user: this.users.getUser(id),
      trips: this.tripService.getSummary(id),
      expenses: this.expenseService.getSummary({ ownerId: id }),
      claims: this.claimService.getSummary(id),
      recentTrips: this.tripService.listTrips({ ...recent, sort: 'startDate,desc' }).pipe(map((p) => p.items)),
      recentExpenses: this.expenseService.listExpenses({ ...recent, sort: 'expenseDate,desc' }).pipe(map((p) => p.items)),
      recentClaims: this.claimService.listClaims({ ...recent, sort: 'submittedAt,desc' }).pipe(map((p) => p.items)),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.state.set('ready');
        },
        error: (error: unknown) =>
          this.state.set(error instanceof HttpErrorResponse && error.status === 404 ? 'not-found' : 'error'),
      });
  }

  statusClass(status: string): string {
    return `status-badge status-${status.toLowerCase().replace(/_/g, '-')}`;
  }

  label(value: string): string {
    const text = value.toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
