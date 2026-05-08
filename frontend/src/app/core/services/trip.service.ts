import { Injectable } from '@angular/core';

import { environment } from '../../../environments/environment';
import { Trip, TripPayload } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';
import { CrudHttpService } from './crud-http.service';

@Injectable({ providedIn: 'root' })
export class TripService {
  constructor(private readonly crudHttp: CrudHttpService) {}

  private readonly endpoint = `${environment.apiBaseUrl}/trips`;

  getTrips(onSuccess?: ApiSuccess<Trip[]>, onError?: ApiError): void {
    runRequest(this.crudHttp.list<Trip>(this.endpoint), onSuccess, onError);
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
