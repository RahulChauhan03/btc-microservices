import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MOCK_TRIPS } from '../data/mock-data';
import { Trip, TripPayload } from '../models/domain.models';
import { ApiError, ApiSuccess, runRequest } from './api-callbacks';

@Injectable({ providedIn: 'root' })
export class TripService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/trips`;
  private readonly useMockData = environment.useMockData;
  private trips: Trip[] = structuredClone(MOCK_TRIPS);

  getTrips(onSuccess?: ApiSuccess<Trip[]>, onError?: ApiError): void {
    runRequest(this.getTripsRequest(), onSuccess, onError);
  }

  createTrip(payload: TripPayload, onSuccess?: ApiSuccess<Trip>, onError?: ApiError): void {
    runRequest(this.createTripRequest(payload), onSuccess, onError);
  }

  updateTrip(id: number, payload: TripPayload, onSuccess?: ApiSuccess<Trip>, onError?: ApiError): void {
    runRequest(this.updateTripRequest(id, payload), onSuccess, onError);
  }

  deleteTrip(id: number, onSuccess?: ApiSuccess<void>, onError?: ApiError): void {
    runRequest(this.deleteTripRequest(id), onSuccess, onError);
  }

  private getTripsRequest(): Observable<Trip[]> {
    if (this.useMockData) {
      return of(structuredClone(this.trips));
    }

    return this.http.get<Trip[]>(this.endpoint);
  }

  private createTripRequest(payload: TripPayload): Observable<Trip> {
    if (this.useMockData) {
      const trip: Trip = {
        id: this.nextId(),
        ...payload,
      };
      this.trips = [trip, ...this.trips];
      return of(structuredClone(trip));
    }

    return this.http.post<Trip>(this.endpoint, payload);
  }

  private updateTripRequest(id: number, payload: TripPayload): Observable<Trip> {
    if (this.useMockData) {
      const updatedTrip: Trip = { id, ...payload };
      this.trips = this.trips.map((trip) => (trip.id === id ? updatedTrip : trip));
      return of(structuredClone(updatedTrip));
    }

    return this.http.put<Trip>(`${this.endpoint}/${id}`, payload);
  }

  private deleteTripRequest(id: number): Observable<void> {
    if (this.useMockData) {
      this.trips = this.trips.filter((trip) => trip.id !== id);
      return of(void 0);
    }

    return this.http.delete<void>(`${this.endpoint}/${id}`);
  }

  private nextId(): number {
    return Math.max(...this.trips.map((trip) => trip.id), 100) + 1;
  }
}
