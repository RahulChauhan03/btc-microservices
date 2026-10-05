import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { AuthUser } from '../../../../core/models/auth.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ClaimService } from '../../../../core/services/claim.service';
import { ExpenseService } from '../../../../core/services/expense.service';
import { TripService } from '../../../../core/services/trip.service';
import { UserService } from '../../../../core/services/user.service';
import { TripDetailsComponent } from './trip-details.component';

describe('TripDetailsComponent', () => {
  const trip = { id: 4, tripCode: 'T-4', destination: 'Pune', startDate: '2026-11-01', endDate: '2026-11-03', status: 'PLANNED', budget: 100, ownerId: 7 };
  const tripService = { getTrip: vi.fn(), deleteTrip: vi.fn() };
  const expenseService = { listExpenses: vi.fn(), getSummary: vi.fn() };
  const claimService = { listClaims: vi.fn() };
  const userService = { getUser: vi.fn() };

  function render(me: AuthUser, expenseCount = 1) {
    vi.clearAllMocks();
    tripService.getTrip.mockReturnValue(of(trip));
    const expense = { id: 9, title: 'Taxi', category: 'TRANSPORT', amount: 150, expenseDate: '2026-11-01', claimId: null };
    expenseService.listExpenses.mockReturnValue(of({ items: expenseCount ? [expense] : [], total: expenseCount }));
    expenseService.getSummary.mockReturnValue(of({ total: expenseCount ? 150 : 0, count: expenseCount, byCategory: [], byMonth: [] }));
    claimService.listClaims.mockReturnValue(of({ items: [], total: 0 }));
    userService.getUser.mockReturnValue(of({ id: 7, name: 'Owner Person' }));
    TestBed.configureTestingModule({
      imports: [TripDetailsComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: new BehaviorSubject(convertToParamMap({ id: '4' })), snapshot: { paramMap: convertToParamMap({ id: '4' }) } } },
        { provide: AuthService, useValue: { currentUser: signal(me) } },
        { provide: TripService, useValue: tripService },
        { provide: ExpenseService, useValue: expenseService },
        { provide: ClaimService, useValue: claimService },
        { provide: UserService, useValue: userService },
      ],
    });
    const fixture = TestBed.createComponent(TripDetailsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('loads the trip with its expenses, claims and server-computed spending', () => {
    const el: HTMLElement = render({ id: 7, name: 'Owner Person', email: 'o@x.test', role: 'EMPLOYEE' }).nativeElement;

    expect(expenseService.listExpenses).toHaveBeenCalledWith(expect.objectContaining({ tripId: 4 }));
    expect(claimService.listClaims).toHaveBeenCalledWith(expect.objectContaining({ tripId: 4 }));
    expect(el.textContent).toContain('Pune');
    expect(el.textContent).toContain('Taxi');
    expect(el.textContent).toContain('$150.00');
    expect(el.textContent).toContain('over this trip’s budget');
    expect(el.textContent).toContain('Owner Person (you)');
  });

  it('lets the owner edit, but explains why a trip with expenses cannot be deleted', () => {
    const el: HTMLElement = render({ id: 7, name: 'Owner Person', email: 'o@x.test', role: 'EMPLOYEE' }).nativeElement;
    const remove = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Delete')) as HTMLButtonElement;

    expect(el.querySelector('a[href="/trips/edit/4"]')).not.toBeNull();
    expect(remove.disabled).toBe(true);
    expect(el.textContent).toContain('can’t be deleted');
  });

  it('shows administrators the owner name but no owner-only actions', () => {
    const el: HTMLElement = render({ id: 1, name: 'Admin', email: 'a@x.test', role: 'ADMIN' }).nativeElement;

    expect(userService.getUser).toHaveBeenCalledWith(7);
    expect(el.textContent).toContain('Owner Person');
    expect(el.querySelector('a[href="/trips/edit/4"]')).toBeNull();
  });

  it('shows not-found for a trip the caller may not see', () => {
    const fixture = render({ id: 8, name: 'Other', email: 'x@x.test', role: 'EMPLOYEE' });
    tripService.getTrip.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    fixture.componentInstance.load(4);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Trip not found');
  });
});
