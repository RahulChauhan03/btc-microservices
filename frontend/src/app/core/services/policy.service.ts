import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { inlineFeedbackRequest } from '../http/request-context';
import { TravelPolicy, TravelPolicyPayload } from '../models/domain.models';
import { CrudHttpService } from './crud-http.service';

/** Travel policy (expense-service): readable by everyone, changed by administrators only (server-enforced). */
@Injectable({ providedIn: 'root' })
export class PolicyService {
  private readonly http = inject(HttpClient);
  private readonly crudHttp = inject(CrudHttpService);
  private readonly endpoint = `${environment.apiBaseUrl}/expenses/policies`;

  list(): Observable<TravelPolicy[]> {
    return this.crudHttp.fetch<TravelPolicy[]>(this.endpoint);
  }

  create(payload: TravelPolicyPayload): Observable<TravelPolicy> {
    return this.http.post<TravelPolicy>(this.endpoint, payload, { context: inlineFeedbackRequest() });
  }

  update(id: number, payload: TravelPolicyPayload): Observable<TravelPolicy> {
    return this.http.put<TravelPolicy>(`${this.endpoint}/${id}`, payload, { context: inlineFeedbackRequest() });
  }
}
