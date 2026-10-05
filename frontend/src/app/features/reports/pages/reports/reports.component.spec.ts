import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ReportService } from '../../../../core/services/report.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TripService } from '../../../../core/services/trip.service';
import { ReportsComponent } from './reports.component';

describe('ReportsComponent', () => {
  const reports = { expenseSummary: vi.fn(), claimStatus: vi.fn(), spendingByTrip: vi.fn(), exportCsv: vi.fn() };
  const trips = { listTrips: vi.fn() };

  function render() {
    vi.clearAllMocks();
    reports.expenseSummary.mockReturnValue(of({ from: '2026-01-01', to: '2026-03-31', count: 3, total: 165, byCategory: [{ category: 'HOTEL', count: 1, total: 100 }], byMonth: [{ year: 2026, month: 2, count: 1, total: 100 }] }));
    reports.claimStatus.mockReturnValue(of({ total: 2, totalAmount: 30, byStatus: [{ status: 'APPROVED', count: 1, total: 20 }] }));
    reports.spendingByTrip.mockReturnValue(of({ items: [{ tripId: 7, count: 2, total: 15 }], total: 1 }));
    trips.listTrips.mockReturnValue(of({ items: [{ id: 7, tripCode: 'T-7', destination: 'Goa', budget: 500 }], total: 1 }));
    TestBed.configureTestingModule({
      imports: [ReportsComponent],
      providers: [provideRouter([]), { provide: ReportService, useValue: reports }, { provide: TripService, useValue: trips },
        { provide: ToastService, useValue: { error: vi.fn() } }],
    });
    const fixture = TestBed.createComponent(ReportsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('loads backend aggregates for the default 12-month range and names trips with one batch lookup', () => {
    const el: HTMLElement = render().nativeElement;

    expect(reports.expenseSummary).toHaveBeenCalledWith(expect.objectContaining({ from: expect.stringMatching(/^\d{4}-\d{2}-01$/) }));
    expect(trips.listTrips).toHaveBeenCalledTimes(1);
    expect(trips.listTrips).toHaveBeenCalledWith(expect.objectContaining({ ids: ['7'] }));
    expect(el.textContent).toContain('$165');
    expect(el.textContent).toContain('Feb 2026');
    expect(el.textContent).toContain('Hotel');
    expect(el.textContent).toContain('T-7');
    expect(el.textContent).toContain('Goa');
  });

  it('validates the range before calling the backend', () => {
    const fixture = render();
    reports.expenseSummary.mockClear();
    fixture.componentInstance.form.setValue({ from: '2026-05-01', to: '2026-01-01' });
    fixture.componentInstance.run();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('must be on or before');

    fixture.componentInstance.form.setValue({ from: '2020-01-01', to: '2026-01-01' });
    fixture.componentInstance.run();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('at most two years');
    expect(reports.expenseSummary).not.toHaveBeenCalled();
  });

  it('keeps the expense reports when the claims report fails and says which service failed', () => {
    const fixture = render();
    reports.claimStatus.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
    fixture.componentInstance.run();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('$165');
    expect(el.textContent).toContain('Feb 2026');
    expect(el.textContent).toContain('T-7');
    expect(el.textContent).toContain('claims service doesn’t provide this data');
    expect(el.textContent).not.toContain('couldn’t build the reports');
  });

  it('shows the page error with both causes only when every report fails', () => {
    const fixture = render();
    reports.expenseSummary.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    reports.claimStatus.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    fixture.componentInstance.run();
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role=alert]')?.textContent ?? '';
    expect(alert).toContain('couldn’t build the reports');
    expect(alert).toContain('expense service is unavailable');
    expect(alert).toContain('claims service is unavailable');
  });
});
