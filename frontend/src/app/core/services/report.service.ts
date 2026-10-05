import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { inlineFeedbackRequest } from '../http/request-context';
import { ClaimStatusReport, ExpenseSummary, PagedResult, TripSpend } from '../models/domain.models';
import { CrudHttpService, toHttpParams } from './crud-http.service';

export interface ReportRange {
  from: string;
  to: string;
}

/** Administrator-only reports; every figure is aggregated by the backend for a bounded date range. */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly crudHttp = inject(CrudHttpService);
  private readonly expenses = `${environment.apiBaseUrl}/expenses/reports`;
  private readonly claims = `${environment.apiBaseUrl}/claims/reports`;

  expenseSummary(range: ReportRange): Observable<ExpenseSummary> {
    return this.crudHttp.fetch<ExpenseSummary>(`${this.expenses}/summary`, { ...range });
  }

  spendingByTrip(range: ReportRange, page: number, size: number): Observable<PagedResult<TripSpend>> {
    return this.crudHttp.page<TripSpend>(`${this.expenses}/by-trip`, { ...range, page, size });
  }

  claimStatus(range: ReportRange): Observable<ClaimStatusReport> {
    return this.crudHttp.fetch<ClaimStatusReport>(`${this.claims}/status`, { ...range });
  }

  /** The CSV as a Blob (the request carries the JWT, so a plain link would not be authorised). */
  exportCsv(range: ReportRange): Observable<Blob> {
    return this.http.get(`${this.expenses}/export.csv`, {
      params: toHttpParams({ ...range }),
      responseType: 'blob',
      context: inlineFeedbackRequest(),
    });
  }
}
