import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MOCK_CLAIMS } from '../data/mock-data';
import { Claim, ClaimPayload } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';

@Injectable({ providedIn: 'root' })
export class ClaimService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/claims`;
  private readonly useMockData = environment.useMockData;
  private claims: Claim[] = structuredClone(MOCK_CLAIMS);

  getClaims(onSuccess?: ApiSuccess<Claim[]>, onError?: ApiError): void {
    runRequest(this.getClaimsRequest(), onSuccess, onError);
  }

  submitClaim(payload: ClaimPayload, onSuccess?: ApiSuccess<Claim>, onError?: ApiError): void {
    runRequest(this.submitClaimRequest(payload), onSuccess, onError);
  }

  updateClaim(id: number, payload: ClaimPayload, onSuccess?: ApiSuccess<Claim>, onError?: ApiError): void {
    runRequest(this.updateClaimRequest(id, payload), onSuccess, onError);
  }

  deleteClaim(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.deleteClaimRequest(id), onSuccess, onError);
  }

  private getClaimsRequest(): Observable<Claim[]> {
    if (this.useMockData) {
      return of(structuredClone(this.claims));
    }

    return this.http.get<Claim[]>(this.endpoint);
  }

  private submitClaimRequest(payload: ClaimPayload): Observable<Claim> {
    if (this.useMockData) {
      const claim: Claim = {
        id: this.nextId(),
        tripId: payload.tripId,
        claimNumber: `CLM-2026-${String(this.claims.length + 1).padStart(3, '0')}`,
        totalAmount: 250 + payload.tripId,
        submittedOn: new Date().toISOString().slice(0, 10),
        status: 'SUBMITTED',
        approver: 'Priya Nair',
        comment: payload.comment,
      };
      this.claims = [claim, ...this.claims];
      return of(structuredClone(claim));
    }

    return this.http.post<Claim>(this.endpoint, payload);
  }

  private updateClaimRequest(id: number, payload: ClaimPayload): Observable<Claim> {
    if (this.useMockData) {
      const existingClaim = this.claims.find((claim) => claim.id === id);
      const updatedClaim: Claim = {
        id,
        tripId: payload.tripId,
        claimNumber: existingClaim?.claimNumber ?? `CLM-2026-${String(this.claims.length).padStart(3, '0')}`,
        totalAmount: existingClaim?.totalAmount ?? 250 + payload.tripId,
        submittedOn: existingClaim?.submittedOn ?? new Date().toISOString().slice(0, 10),
        status: existingClaim?.status ?? 'SUBMITTED',
        approver: existingClaim?.approver ?? 'Priya Nair',
        comment: payload.comment,
      };
      this.claims = this.claims.map((claim) => (claim.id === id ? updatedClaim : claim));
      return of(structuredClone(updatedClaim));
    }

    return this.http.put<Claim>(`${this.endpoint}/${id}`, payload);
  }

  private deleteClaimRequest(id: number): Observable<void> {
    if (this.useMockData) {
      this.claims = this.claims.filter((claim) => claim.id !== id);
      return of(void 0);
    }

    return this.http.delete<void>(`${this.endpoint}/${id}`);
  }

  private nextId(): number {
    return Math.max(...this.claims.map((claim) => claim.id), 300) + 1;
  }
}
