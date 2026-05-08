import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

import { ClaimService } from '../../../../core/services/claim.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { TripService } from '../../../../core/services/trip.service';
import { DashboardSummary } from '../../../../core/models/domain.models';
import { StatCardComponent } from '../../../../shared/components/stat-card/stat-card.component';

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    RouterLink,
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    StatCardComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  private readonly tripService = inject(TripService);
  private readonly expenseService = inject(ExpenseService);
  private readonly claimService = inject(ClaimService);

  readonly trips = signal<any[]>([]);
  readonly expenses = signal<any[]>([]);
  readonly claims = signal<any[]>([]);

  readonly summary = computed<DashboardSummary>(() => {
    const trips = this.trips();
    const claims = this.claims();
    const expenses = this.expenses();
    const approvedClaims = claims.filter((claim) => claim.status === 'APPROVED').length;

    return {
      activeTrips: trips.filter((trip) => ['PLANNED', 'IN_PROGRESS'].includes(trip.status)).length,
      pendingClaims: claims.filter((claim) => ['SUBMITTED', 'UNDER_REVIEW'].includes(claim.status)).length,
      monthlyExpenses: expenses.reduce((total, expense) => total + Number(expense.amount ?? 0), 0),
      approvalRate: claims.length ? (approvedClaims / claims.length) * 100 : 0,
    };
  });

  readonly topTrips = computed(() => this.trips().slice(0, 5));
  readonly latestClaims = computed(() => this.claims().slice(0, 5));

  constructor() {
    this.loadDashboard();
  }

  statusClass(status: string): string {
    return `status-badge status-${status.toLowerCase().replace(/_/g, '-')}`;
  }

  private loadDashboard(): void {
    this.tripService.getTrips(
      (trips) => this.trips.set(trips),
      () => this.trips.set([]),
    );
    this.expenseService.getExpenses(
      (expenses) => this.expenses.set(expenses),
      () => this.expenses.set([]),
    );
    this.claimService.getClaims(
      (claims) => this.claims.set(claims),
      () => this.claims.set([]),
    );
  }
}
