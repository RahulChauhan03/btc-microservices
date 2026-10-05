import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthUser } from '../../../../core/models/auth.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ClaimService } from '../../../../core/services/claim.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { TripService } from '../../../../core/services/trip.service';
import { UserService } from '../../../../core/services/user.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  const trips = { getSummary: vi.fn(), listTrips: vi.fn() };
  const expenses = { getSummary: vi.fn(), listExpenses: vi.fn() };
  const claims = { getSummary: vi.fn(), listClaims: vi.fn() };
  const users = { getStats: vi.fn() };

  function render(role: AuthUser['role']) {
    vi.clearAllMocks();
    trips.getSummary.mockReturnValue(of({ total: 3, upcoming: 2, byStatus: [] }));
    trips.listTrips.mockReturnValue(of({ items: [{ id: 4, tripCode: 'T-4', destination: 'Pune', startDate: '2026-11-01', endDate: '2026-11-03', status: 'PLANNED', budget: 500, ownerId: 1 }], total: 1 }));
    expenses.getSummary.mockReturnValue(of({ from: '2025-11-01', to: '2026-10-02', count: 3, total: 115, byCategory: [{ category: 'MEAL', count: 2, total: 15 }], byMonth: [{ year: 2026, month: 1, count: 2, total: 15 }] }));
    expenses.listExpenses.mockReturnValue(of({ items: [], total: 0 }));
    claims.getSummary.mockReturnValue(of({ total: 2, totalAmount: 30, awaitingReview: 1, byStatus: [{ status: 'APPROVED', count: 1, total: 20 }, { status: 'SUBMITTED', count: 1, total: 10 }] }));
    claims.listClaims.mockReturnValue(of({ items: [], total: 0 }));
    users.getStats.mockReturnValue(of({ total: 5, admins: 1, employees: 4, joinedLast30Days: 2 }));
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: signal<AuthUser>({ id: 1, name: 'Asha Rao', email: 'a@x.test', role }) } },
        { provide: TripService, useValue: trips },
        { provide: ExpenseService, useValue: expenses },
        { provide: ClaimService, useValue: claims },
        { provide: UserService, useValue: users },
      ],
    });
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows an employee their own figures from the backend and no admin-only data', () => {
    const el: HTMLElement = render('EMPLOYEE').nativeElement;

    expect(el.textContent).toContain('Good');
    expect(el.textContent).toContain('My spending');
    expect(el.textContent).toContain('$115');
    expect(el.textContent).toContain('My claims');
    expect(el.textContent).toContain('Pune');
    expect(el.textContent).not.toContain('Pending approvals');
    expect(users.getStats).not.toHaveBeenCalled();
    expect(claims.listClaims).not.toHaveBeenCalledWith(expect.objectContaining({ status: ['SUBMITTED', 'PENDING'] }));
  });

  it('shows administrators company-wide sections', () => {
    const el: HTMLElement = render('ADMIN').nativeElement;

    expect(el.textContent).toContain('Pending approvals');
    expect(el.textContent).toContain('Monthly spending');
    expect(el.textContent).toContain('Jan 2026');
    expect(el.textContent).toContain('Team');
    expect(users.getStats).toHaveBeenCalled();
  });

  it('keeps the rest of the dashboard when one service fails, names it, and retries', () => {
    const fixture = render('ADMIN');
    const outdated = new HttpErrorResponse({ status: 400, error: { message: "Invalid value for 'id'" } });
    claims.getSummary.mockReturnValueOnce(throwError(() => outdated));
    claims.listClaims.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    fixture.componentInstance.load();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.partial-alert')?.textContent).toContain('claims service');
    expect(el.textContent).toContain('Company spending');
    expect(el.textContent).toContain('$115');
    expect(el.textContent).toContain('Pune');
    expect(el.textContent).toContain('Unavailable');
    expect(el.textContent).not.toContain('couldn’t load your dashboard');

    (el.querySelector('.partial-alert button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('.partial-alert')).toBeNull();
  });

  it('shows the full error with a working retry only when nothing could be loaded', () => {
    const fixture = render('EMPLOYEE');
    const down = () => throwError(() => new HttpErrorResponse({ status: 0 }));
    for (const fn of [trips.getSummary, trips.listTrips, expenses.getSummary, expenses.listExpenses, claims.getSummary, claims.listClaims]) {
      fn.mockReturnValueOnce(down());
    }
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain('couldn’t load your dashboard');

    fixture.nativeElement.querySelector('[role=alert] button').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('My spending');
  });
});
