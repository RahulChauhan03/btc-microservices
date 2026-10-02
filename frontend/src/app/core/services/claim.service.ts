import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { Claim, ClaimPayload } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class ClaimService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/claims`;

  getClaims(onSuccess?: ApiSuccess<Claim[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<Claim>(this.endpoint), onSuccess, onError);
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
