import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { inlineFeedbackRequest } from '../http/request-context';
import { PagedResult, Reimbursement, ReimbursementUpdate } from '../models/domain.models';
import { CrudHttpService, QueryParams } from './crud-http.service';

/** Payment records of approved claims (claim-service). Employees read their own; administrators update. */
@Injectable({ providedIn: 'root' })
export class ReimbursementService {
  private readonly http = inject(HttpClient);
  private readonly crudHttp = inject(CrudHttpService);
  private readonly base = `${environment.apiBaseUrl}/claims`;

  list(params: QueryParams): Observable<PagedResult<Reimbursement>> {
    return this.crudHttp.page<Reimbursement>(`${this.base}/reimbursements`, params);
  }

  update(claimId: number, payload: ReimbursementUpdate): Observable<Reimbursement> {
    return this.http.put<Reimbursement>(`${this.base}/${claimId}/reimbursement`, payload, { context: inlineFeedbackRequest() });
  }
}
