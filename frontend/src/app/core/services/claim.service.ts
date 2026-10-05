import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Claim, ClaimPayload, ClaimSummary, PagedResult } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService, QueryParams } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class ClaimService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/claims`;

  /** Filters: ownerId (administrators), tripId, status (one or more), page, size, sort. */
  listClaims(params: QueryParams): Observable<PagedResult<Claim>> {
    return this.crudHttp.page<Claim>(this.endpoint, params);
  }

  getClaim(id: number): Observable<Claim> {
    return this.crudHttp.fetch<Claim>(`${this.endpoint}/${id}`);
  }

  getSummary(ownerId?: number | null): Observable<ClaimSummary> {
    return this.crudHttp.fetch<ClaimSummary>(`${this.endpoint}/summary`, { ownerId });
  }

  getClaims(onSuccess?: ApiSuccess<Claim[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<Claim>(this.endpoint, 'Loading claims…'), onSuccess, onError);
  }

  submitClaim(payload: ClaimPayload, onSuccess?: ApiSuccess<Claim>, onError?: ApiError): void {
    runRequest(this.crudHttp.create<Claim, ClaimPayload>(this.endpoint, payload), onSuccess, onError);
  }

  updateClaim(id: number, payload: ClaimPayload, onSuccess?: ApiSuccess<Claim>, onError?: ApiError): void {
    runRequest(this.crudHttp.update<Claim, ClaimPayload>(this.endpoint, id, payload), onSuccess, onError);
  }

  deleteClaim(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.crudHttp.delete(this.endpoint, id), onSuccess, onError);
  }

  /** Administrator only, and never on one's own claim; enforced by claim-service. */
  approveClaim(id: number, onSuccess?: ApiSuccess<Claim>, onError?: ApiError): void {
    runRequest(this.http.post<Claim>(`${this.endpoint}/${id}/approve`, null), onSuccess, onError);
  }

  rejectClaim(id: number, onSuccess?: ApiSuccess<Claim>, onError?: ApiError): void {
    runRequest(this.http.post<Claim>(`${this.endpoint}/${id}/reject`, null), onSuccess, onError);
  }
}
