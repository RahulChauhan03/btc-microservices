import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PagedResult, Trip, TripPayload, TripSummary } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService, QueryParams } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class TripService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly endpoint = `${environment.apiBaseUrl}/trips`;

  /** Filters: ownerId (administrators), status, upcoming, page, size, sort. */
  listTrips(params: QueryParams): Observable<PagedResult<Trip>> {
    return this.crudHttp.page<Trip>(this.endpoint, params);
  }

  getTrip(id: number): Observable<Trip> {
    return this.crudHttp.fetch<Trip>(`${this.endpoint}/${id}`);
  }

  getSummary(ownerId?: number | null): Observable<TripSummary> {
    return this.crudHttp.fetch<TripSummary>(`${this.endpoint}/summary`, { ownerId });
  }

  getTrips(onSuccess?: ApiSuccess<Trip[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<Trip>(this.endpoint, 'Loading trips…'), onSuccess, onError);
  }

  createTrip(payload: TripPayload, onSuccess?: ApiSuccess<Trip>, onError?: ApiError): void {
    runRequest(this.crudHttp.create<Trip, TripPayload>(this.endpoint, payload), onSuccess, onError);
  }

  updateTrip(id: number, payload: TripPayload, onSuccess?: ApiSuccess<Trip>, onError?: ApiError): void {
    runRequest(this.crudHttp.update<Trip, TripPayload>(this.endpoint, id, payload), onSuccess, onError);
  }

  deleteTrip(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.crudHttp.delete(this.endpoint, id), onSuccess, onError);
  }
}
